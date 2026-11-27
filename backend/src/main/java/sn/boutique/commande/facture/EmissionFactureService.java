package sn.boutique.commande.facture;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.commande.facture.FactureDepot.FactureArchivee;
import sn.boutique.commande.facture.FactureDepot.NouvelleFacture;
import sn.boutique.commande.facture.FactureDepot.NouvelleLigne;
import sn.boutique.commande.facture.FactureDepot.TypeLigne;
import sn.boutique.commande.modele.AdresseLivraison;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.LigneCommande;
import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.modele.ParametresSite;
import sn.boutique.contenu.modele.RegimeTva;

/**
 * Émission de la facture d'une commande, <strong>dans la transaction appelante</strong> (paiement en ligne confirmé,
 * livraison avec encaissement) : numéro, lignes, montants, PDF et événement sont validés ou annulés ensemble, donc
 * aucun numéro perdu et aucune commande payée sans facture.
 * <p>
 * Tout est <strong>figé à l'émission</strong> : vendeur (raison sociale, NINEA, RCCM, siège), régime et taux de TVA,
 * client, lignes, montants, et le PDF lui-même (octets conservés). Modifier ensuite les paramètres du site, le
 * catalogue ou le client ne change rien à une facture émise ; la base interdit d'ailleurs toute modification.
 * <p>
 * Idempotent : une commande n'a qu'une facture ; un second appel renvoie la première.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmissionFactureService {

    private final FactureDepot factureDepot;
    private final CompteurDocumentService compteur;
    private final ParametresSiteDepot parametresDepot;
    private final RenduPdfService rendu;
    private final VentesProduitService ventes;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    /** Publié à l'émission (dans la transaction) : l'envoi par e-mail se fait après validation. */
    public record FactureEmise(long factureId, long commandeId, String numero) {
    }

    /**
     * @param commande         commande verrouillée par l'appelant
     * @param precisionPaiement précision du moyen de paiement imprimée sur la facture (« espèces », « Wave au
     *                         livreur »), ou null
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public FactureArchivee emettre(Commande commande, String precisionPaiement) {
        var existante = factureDepot.trouverParCommande(commande.getId());
        if (existante.isPresent()) {
            return existante.get();
        }
        ParametresSite site = parametresDepot.lire();
        RegimeTva regime = site.getFactureRegimeTva();
        int tauxBp = regime == RegimeTva.ASSUJETTI ? site.getFactureTauxTvaBp() : 0;

        List<Montants> montants = montants(commande, tauxBp);
        long totalTtc = montants.stream().mapToLong(Montants::ttc).sum();
        long totalTva = montants.stream().mapToLong(Montants::tva).sum();
        long totalHt = totalTtc - totalTva;
        if (totalTtc != commande.getTotal()) {
            throw new IllegalStateException("Incohérence de la commande %s : lignes %d FCFA, total %d FCFA"
                    .formatted(commande.getNumero(), totalTtc, commande.getTotal()));
        }

        Instant maintenant = horloge.instant();
        String numero = compteur.prochainNumero(TypeDocument.FACTURE);
        List<NouvelleLigne> lignes = lignes(commande, montants);
        String libelleMode = libelleMode(commande.getModePaiement(), precisionPaiement);
        String adresseClient = adresseClient(commande);
        String nomClient = nomClient(commande);

        var vendeur = new PieceImprimable.Vendeur(site.getFactureRaisonSociale(), site.getFactureNinea(),
                site.getFactureRccm(), site.getFactureAdresseSiege());
        var client = new PieceImprimable.Client(nomClient, commande.getTelephoneClient(), commande.getEmailClient(),
                adresseClient);
        var piece = new PieceImprimable("Facture", numero, maintenant, commande.getNumero(), null, null, vendeur,
                client, libelleMode, regime, site.getFactureTauxTvaBp(), site.getFactureMentionTva(),
                lignes.stream().map(EmissionFactureService::versLigneImprimee).toList(), totalTtc, totalHt, totalTva);
        byte[] pdf = rendu.rendre(piece);

        long id = factureDepot.inserer(new NouvelleFacture(numero, commande.getId(), maintenant,
                site.getFactureRaisonSociale(), site.getFactureNinea(), site.getFactureRccm(),
                site.getFactureAdresseSiege(), regime, site.getFactureTauxTvaBp(), site.getFactureMentionTva(),
                nomClient, commande.getTelephoneClient(), commande.getEmailClient(), adresseClient,
                commande.getModePaiement().name(), totalTtc, totalHt, totalTva, pdf, sha256(pdf), lignes));
        ventes.compterFacture(id);
        evenements.publishEvent(new FactureEmise(id, commande.getId(), numero));
        log.info("Facture {} émise pour la commande {} ({} FCFA)", numero, commande.getNumero(), totalTtc);
        return factureDepot.trouverParCommande(commande.getId()).orElseThrow();
    }

    /** TTC net et TVA de chaque ligne : les articles puis, s'il y en a, la livraison. */
    private record Montants(long brut, long remise, long ttc, long tva) {
    }

    private static List<Montants> montants(Commande commande, int tauxBp) {
        List<LigneCommande> articles = commande.getLignes();
        long sousTotal = articles.stream().mapToLong(LigneCommande::getTotalLigne).sum();
        long frais = commande.getFraisLivraison();
        // La remise (code promo) s'impute d'abord sur les articles, au prorata ; l'excédent éventuel sur la livraison
        long remiseTotale = Math.min(commande.getRemise(), sousTotal + frais);
        long remiseArticles = Math.min(remiseTotale, sousTotal);
        long remiseLivraison = remiseTotale - remiseArticles;

        long[] remises = CalculFacture.repartir(remiseArticles,
                articles.stream().map(LigneCommande::getTotalLigne).toList());
        List<Long> bruts = new ArrayList<>();
        List<Long> remisesLignes = new ArrayList<>();
        List<Long> ttc = new ArrayList<>();
        for (int i = 0; i < articles.size(); i++) {
            long brut = articles.get(i).getTotalLigne();
            bruts.add(brut);
            remisesLignes.add(remises[i]);
            ttc.add(brut - remises[i]);
        }
        if (frais > 0) {
            bruts.add(frais);
            remisesLignes.add(remiseLivraison);
            ttc.add(frais - remiseLivraison);
        }
        List<CalculFacture.Part> parts = CalculFacture.extraireTva(ttc, tauxBp);
        List<Montants> resultat = new ArrayList<>();
        for (int i = 0; i < ttc.size(); i++) {
            resultat.add(new Montants(bruts.get(i), remisesLignes.get(i), ttc.get(i), parts.get(i).tva()));
        }
        return resultat;
    }

    private static List<NouvelleLigne> lignes(Commande commande, List<Montants> montants) {
        List<NouvelleLigne> lignes = new ArrayList<>();
        List<LigneCommande> articles = commande.getLignes();
        for (int i = 0; i < montants.size(); i++) {
            Montants m = montants.get(i);
            if (i < articles.size()) {
                LigneCommande a = articles.get(i);
                String designation = a.getLibelleVariante() == null || a.getLibelleVariante().isBlank()
                        ? a.getNomProduit() : a.getNomProduit() + " – " + a.getLibelleVariante();
                lignes.add(new NouvelleLigne(TypeLigne.ARTICLE, a.getId(), a.getSku(), designation, a.getQuantite(),
                        a.getPrixUnitaire(), m.brut(), m.remise(), m.ttc(), m.ttc() - m.tva(), m.tva()));
            } else {
                String zone = commande.getZoneLivraison() == null ? "" : " – " + commande.getZoneLivraison().getNom();
                lignes.add(new NouvelleLigne(TypeLigne.LIVRAISON, null, null, "Livraison" + zone, 1, m.brut(),
                        m.brut(), m.remise(), m.ttc(), m.ttc() - m.tva(), m.tva()));
            }
        }
        return lignes;
    }

    private static PieceImprimable.Ligne versLigneImprimee(NouvelleLigne l) {
        return new PieceImprimable.Ligne(l.sku(), l.designation(), l.quantite(), l.prixUnitaireTtc(), l.remiseTtc(),
                l.totalTtc(), l.totalHt(), l.montantTva());
    }

    private static String nomClient(Commande commande) {
        if (commande.getNomClient() != null && !commande.getNomClient().isBlank()) {
            return commande.getNomClient();
        }
        AdresseLivraison adresse = commande.getAdresseLivraison();
        return adresse == null ? null : adresse.getNomComplet();
    }

    private static String adresseClient(Commande commande) {
        AdresseLivraison a = commande.getAdresseLivraison();
        if (a == null) {
            return null;
        }
        String texte = String.join(", ", Stream.of(a.getAdresseLigne(), a.getQuartier(), a.getVille())
                .filter(Objects::nonNull).filter(s -> !s.isBlank()).toList());
        return texte.isBlank() ? null : texte;
    }

    static String libelleMode(ModePaiement mode, String precision) {
        String base = switch (mode) {
            case MOBILE_MONEY -> "Paiement en ligne (mobile money)";
            case CARTE_BANCAIRE -> "Paiement en ligne (carte bancaire)";
            case A_LA_LIVRAISON -> "Paiement à la livraison";
            case WAVE -> "Wave";
            case ORANGE_MONEY -> "Orange Money";
            default -> mode.name();
        };
        return precision == null || precision.isBlank() ? base : base + " : " + precision;
    }

    private static String sha256(byte[] octets) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
