package sn.boutique.commande.facture;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.facture.AvoirDepot.AvoirArchive;
import sn.boutique.commande.facture.AvoirDepot.Credite;
import sn.boutique.commande.facture.AvoirDepot.LigneAvoir;
import sn.boutique.commande.facture.AvoirDepot.NouvelAvoir;
import sn.boutique.commande.facture.FactureDepot.FactureArchivee;
import sn.boutique.commande.facture.FactureDepot.LigneArchivee;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Émission d'un avoir (note de crédit) sur une facture : total ou partiel, une ou plusieurs lignes.
 * <p>
 * L'avoir référence la facture d'origine et a sa propre numérotation continue ({@code AV-2026-000001}). Il reprend
 * l'identité figée de la facture (vendeur, client, régime de TVA) : les paramètres du site d'aujourd'hui n'y
 * figurent jamais. C'est une <strong>pièce comptable seule</strong> : il ne remet rien en stock et ne rembourse pas
 * (le remboursement se fait à la main chez le prestataire ou en espèces ; le geste « retour client » qui crée l'avoir
 * et la remise en stock d'un seul coup viendra en phase 6).
 * <p>
 * Les montants d'une ligne partiellement créditée sont proportionnels à la quantité, arrondis au franc ; le solde
 * d'une ligne (dernière quantité) reprend exactement ce qui reste, de sorte que le cumul des avoirs ne dépasse
 * jamais la facture et la rejoint exactement quand tout est crédité. La commande passe à {@code REMBOURSEE} quand
 * les avoirs couvrent toute la facture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvoirService {

    /** {@code rang} : numéro de la ligne sur la facture (1, 2…). La livraison est une ligne de quantité 1. */
    public record LigneCreditee(@Min(1) int rang, @Min(1) int quantite) {
    }

    /** Soit {@code total} vrai (tout ce qui reste), soit une liste de lignes. */
    public record AvoirRequete(
            @NotBlank(message = "Le motif est obligatoire")
            @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères") String motif,
            Boolean total,
            List<@Valid LigneCreditee> lignes) {

        boolean toutCrediter() {
            return Boolean.TRUE.equals(total);
        }
    }

    private final FactureDepot factureDepot;
    private final AvoirDepot avoirDepot;
    private final CommandeDepot commandeDepot;
    private final CompteurDocumentService compteur;
    private final RenduPdfService rendu;
    private final ApplicationEventPublisher evenements;
    private final JdbcClient jdbc;
    private final Clock horloge;

    /** Publié à l'émission (dans la transaction). */
    public record AvoirEmis(long avoirId, long factureId, long commandeId, String numero, List<LigneAvoir> lignes) {
    }

    @Transactional
    public AvoirArchive emettre(String numeroFacture, String auteur, AvoirRequete requete) {
        FactureArchivee facture = factureDepot.trouverParNumero(numeroFacture)
                .orElseThrow(() -> new RessourceIntrouvableException("Facture", numeroFacture));
        // Verrou de la commande : deux avoirs simultanés sur la même facture passent l'un après l'autre
        String numeroCommande = jdbc.sql("select numero from commande where id = :i").param("i", facture.commandeId())
                .query(String.class).single();
        Commande commande = commandeDepot.findByNumeroPourMiseAJour(numeroCommande)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", numeroCommande));

        List<LigneArchivee> lignesFacture = factureDepot.lignes(facture.id());
        Map<Long, Credite> credites = avoirDepot.creditesParLigne(facture.id());
        Map<Integer, Integer> demandes = demandes(requete, lignesFacture, credites);

        List<LigneAvoir> lignesAvoir = new ArrayList<>();
        for (LigneArchivee ligne : lignesFacture) {
            Integer quantite = demandes.get(ligne.rang());
            if (quantite == null) {
                continue;
            }
            Credite deja = credites.getOrDefault(ligne.id(), Credite.RIEN);
            int restante = ligne.quantite() - deja.quantite();
            if (quantite > restante) {
                throw new RegleMetierException("QUANTITE_AU_DESSUS_DU_RESTANT",
                        "La ligne %d de la facture %s ne peut plus être créditée que de %d (demandé : %d)."
                                .formatted(ligne.rang(), numeroFacture, restante, quantite));
            }
            LigneAvoir montants = montants(ligne, deja, quantite, restante);
            if (montants.totalTtc() <= 0) {
                if (requete.toutCrediter()) {
                    continue; // article offert : rien à créditer
                }
                throw new RegleMetierException("LIGNE_SANS_MONTANT",
                        "La ligne %d n'a aucun montant à créditer.".formatted(ligne.rang()));
            }
            lignesAvoir.add(montants);
        }
        if (lignesAvoir.isEmpty()) {
            throw new RegleMetierException("AVOIR_VIDE", "Rien à créditer : cette facture est déjà entièrement créditée.");
        }
        long totalTtc = lignesAvoir.stream().mapToLong(LigneAvoir::totalTtc).sum();
        long totalHt = lignesAvoir.stream().mapToLong(LigneAvoir::totalHt).sum();
        long totalTva = lignesAvoir.stream().mapToLong(LigneAvoir::montantTva).sum();
        if (totalTtc <= 0) {
            throw new RegleMetierException("AVOIR_VIDE", "Le montant de l'avoir doit être positif.");
        }

        Instant maintenant = horloge.instant();
        String numero = compteur.prochainNumero(TypeDocument.AVOIR);
        String motif = requete.motif().strip();
        var vendeur = new PieceImprimable.Vendeur(facture.vendeurRaisonSociale(), facture.vendeurNinea(),
                facture.vendeurRccm(), facture.vendeurAdresse());
        var client = new PieceImprimable.Client(facture.clientNom(), facture.clientTelephone(), facture.clientEmail(),
                facture.clientAdresse());
        var lignesImprimees = new ArrayList<PieceImprimable.Ligne>();
        for (LigneAvoir l : lignesAvoir) {
            LigneArchivee origine = lignesFacture.stream().filter(x -> x.id() == l.ligneFactureId()).findFirst()
                    .orElseThrow();
            lignesImprimees.add(new PieceImprimable.Ligne(origine.sku(), origine.designation(), l.quantite(),
                    origine.prixUnitaireTtc(), 0, l.totalTtc(), l.totalHt(), l.montantTva()));
        }
        var piece = new PieceImprimable("Avoir", numero, maintenant, numeroCommande, numeroFacture, motif, vendeur,
                client, EmissionFactureService.libelleMode(ModePaiement.valueOf(facture.modePaiement()), null),
                facture.regimeTva(), facture.tauxTvaBp(), facture.mentionTva(), lignesImprimees, totalTtc, totalHt,
                totalTva);
        byte[] pdf = rendu.rendre(piece);
        long id = avoirDepot.inserer(new NouvelAvoir(numero, facture.id(), maintenant, motif, auteur, totalTtc,
                totalHt, totalTva, pdf, sha256(pdf), lignesAvoir));

        long creditTotal = totalTtc + credites.values().stream().mapToLong(Credite::totalTtc).sum();
        if (creditTotal == facture.totalTtc() && commande.getStatut() != StatutCommande.REMBOURSEE) {
            commande.changerStatut(StatutCommande.REMBOURSEE, auteur,
                    "Facture %s entièrement créditée (avoir %s)".formatted(numeroFacture, numero));
        }
        evenements.publishEvent(new AvoirEmis(id, facture.id(), commande.getId(), numero, lignesAvoir));
        log.info("Avoir {} émis sur la facture {} par {} ({} FCFA)", numero, numeroFacture, auteur, totalTtc);
        return avoirDepot.trouverParNumero(numero).orElseThrow();
    }

    /** Quantité demandée par rang de ligne : « total » = tout le restant de chaque ligne. */
    private static Map<Integer, Integer> demandes(AvoirRequete requete, List<LigneArchivee> lignes,
                                                  Map<Long, Credite> credites) {
        Map<Integer, Integer> demandes = new TreeMap<>();
        if (requete.toutCrediter()) {
            if (requete.lignes() != null && !requete.lignes().isEmpty()) {
                throw new RegleMetierException("AVOIR_AMBIGU", "Choisissez « total » ou des lignes, pas les deux.");
            }
            for (LigneArchivee l : lignes) {
                int restante = l.quantite() - credites.getOrDefault(l.id(), Credite.RIEN).quantite();
                if (restante > 0) {
                    demandes.put(l.rang(), restante);
                }
            }
            return demandes;
        }
        if (requete.lignes() == null || requete.lignes().isEmpty()) {
            throw new RegleMetierException("AVOIR_VIDE", "Indiquez les lignes à créditer, ou « total ».");
        }
        for (LigneCreditee c : requete.lignes()) {
            if (lignes.stream().noneMatch(l -> l.rang() == c.rang())) {
                throw new RegleMetierException("LIGNE_INCONNUE", "La facture n'a pas de ligne " + c.rang() + ".");
            }
            if (demandes.put(c.rang(), c.quantite()) != null) {
                throw new RegleMetierException("LIGNE_EN_DOUBLE", "La ligne " + c.rang() + " est indiquée deux fois.");
            }
        }
        return demandes;
    }

    /** Montants de {@code quantite} unités d'une ligne ; le solde (toutes les unités restantes) reprend le reste exact. */
    static LigneAvoir montants(LigneArchivee ligne, Credite deja, int quantite, int restante) {
        long ttc;
        long tva;
        if (quantite == restante) {
            ttc = ligne.totalTtc() - deja.totalTtc();
            tva = ligne.montantTva() - deja.montantTva();
        } else {
            ttc = proportion(ligne.totalTtc(), quantite, ligne.quantite());
            tva = Math.min(proportion(ligne.montantTva(), quantite, ligne.quantite()), ttc);
        }
        return new LigneAvoir(ligne.id(), quantite, ttc, ttc - tva, tva);
    }

    /** {@code montant × quantite / total}, arrondi au franc le plus proche (demi vers le haut). */
    private static long proportion(long montant, int quantite, int total) {
        return (montant * quantite * 2 + total) / (2L * total);
    }

    private static String sha256(byte[] octets) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
