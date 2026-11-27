package sn.boutique.commande.facture;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.noyau.alerte.AlerteAdminService;
import sn.boutique.noyau.erreur.ConflitException;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Passage d'une commande à {@code LIVREE}, avec l'encaissement réel pour une commande payée à la livraison.
 * <p>
 * Paiement à la livraison : le montant reçu, le moyen (espèces, Wave ou Orange Money au livreur, autre), la personne
 * qui a encaissé et la date sont enregistrés, puis la facture est émise <strong>pour le total de la commande</strong>.
 * Un montant inférieur au total est accepté avec un motif obligatoire ; la commande est alors marquée « écart
 * d'encaissement à régulariser » (alerte admin ; régularisation plus tard par un avoir ou un encaissement
 * complémentaire). Un montant supérieur au total est refusé (422).
 * <p>
 * Commande déjà payée en ligne : aucun encaissement, la facture existe déjà (émise au paiement) ; elle est émise ici
 * seulement si elle manque.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncaissementService {

    /** Statuts depuis lesquels on peut livrer (les routes « préparer » et « expédier » viendront en phase 6). */
    private static final Set<StatutCommande> LIVRABLES = EnumSet.of(StatutCommande.CONFIRMEE, StatutCommande.PAYEE,
            StatutCommande.EN_PREPARATION, StatutCommande.EXPEDIEE);

    public enum MoyenEncaissement {
        ESPECES("espèces"),
        WAVE_LIVREUR("Wave remis au livreur"),
        ORANGE_MONEY_LIVREUR("Orange Money remis au livreur"),
        AUTRE("autre");

        private final String libelle;

        MoyenEncaissement(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    public enum MotifEcart {
        MONNAIE_MANQUANTE, ARTICLE_CONTESTE, REMISE_ACCORDEE, AUTRE
    }

    /**
     * @param montantRecu       FCFA effectivement encaissés (commande à la livraison seulement)
     * @param encaissePar       personne qui a reçu l'argent (le livreur), par défaut l'agent qui enregistre
     * @param motifEcart        obligatoire si le montant est inférieur au total
     */
    public record LivraisonRequete(Long montantRecu, MoyenEncaissement moyen, String moyenPrecision,
                                   String encaissePar, MotifEcart motifEcart, String motifEcartPrecision) {
    }

    public record LivraisonResultat(String numero, String numeroFacture, long total, Long montantRecu,
                                    long ecartEncaissement) {
    }

    private final CommandeDepot commandeDepot;
    private final EmissionFactureService emission;
    private final JdbcClient jdbc;
    private final AlerteAdminService alertes;
    private final Clock horloge;

    @Transactional
    public LivraisonResultat livrer(String numero, String auteur, Long auteurId, LivraisonRequete requete) {
        Commande commande = commandeDepot.findByNumeroPourMiseAJour(numero)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", numero));
        if (commande.getStatut() == StatutCommande.LIVREE) {
            throw new ConflitException("STATUT_INVALIDE", "Cette commande est déjà livrée.");
        }
        if (!LIVRABLES.contains(commande.getStatut())) {
            throw new ConflitException("STATUT_INVALIDE", "Cette commande ne peut pas être livrée (statut : %s)."
                    .formatted(commande.getStatut()));
        }
        boolean aLaLivraison = commande.getModePaiement() == ModePaiement.A_LA_LIVRAISON;
        long ecart = 0;
        Long montantRecu = null;
        String precisionPaiement = null;
        if (aLaLivraison) {
            Encaissement encaissement = verifier(commande, requete);
            ecart = commande.getTotal() - encaissement.montant();
            montantRecu = encaissement.montant();
            precisionPaiement = encaissement.moyen() == MoyenEncaissement.AUTRE ? encaissement.precisionMoyen()
                    : encaissement.moyen().libelle();
            enregistrer(commande, encaissement, auteur, auteurId, ecart);
            commande.setEcartEncaissement(ecart);
        } else if (requete != null && requete.montantRecu() != null) {
            throw new RegleMetierException("DEJA_PAYEE_EN_LIGNE",
                    "Cette commande est payée en ligne : aucun encaissement à enregistrer.");
        }
        var facture = emission.emettre(commande, precisionPaiement);
        commande.changerStatut(StatutCommande.LIVREE, auteur,
                aLaLivraison ? "Livrée, %d FCFA encaissés (%s)".formatted(montantRecu, precisionPaiement)
                        : "Livrée");
        log.info("Commande {} livrée par {} (facture {})", numero, auteur, facture.numero());
        if (ecart > 0) {
            alertes.signaler("Écart d'encaissement à régulariser : commande " + numero,
                    "La commande %s a été livrée, total %d FCFA, encaissé %d FCFA, écart %d FCFA. À régulariser par un avoir ou un encaissement complémentaire."
                            .formatted(numero, commande.getTotal(), montantRecu, ecart));
        }
        return new LivraisonResultat(numero, facture.numero(), commande.getTotal(), montantRecu, ecart);
    }

    private record Encaissement(long montant, MoyenEncaissement moyen, String precisionMoyen, String encaissePar,
                                MotifEcart motif, String precisionMotif) {
    }

    private Encaissement verifier(Commande commande, LivraisonRequete r) {
        if (r == null || r.montantRecu() == null || r.moyen() == null) {
            throw new RegleMetierException("ENCAISSEMENT_REQUIS",
                    "Commande à la livraison : indiquez le montant encaissé et le moyen de paiement.");
        }
        long total = commande.getTotal();
        if (r.montantRecu() < 0) {
            throw new RegleMetierException("MONTANT_INVALIDE", "Le montant encaissé ne peut pas être négatif.");
        }
        if (r.montantRecu() > total) {
            throw new RegleMetierException("MONTANT_SUPERIEUR_AU_TOTAL",
                    "Le montant encaissé (%d FCFA) dépasse le total de la commande (%d FCFA)."
                            .formatted(r.montantRecu(), total));
        }
        String precisionMoyen = nettoyer(r.moyenPrecision());
        if (r.moyen() == MoyenEncaissement.AUTRE && precisionMoyen == null) {
            throw new RegleMetierException("MOYEN_A_PRECISER", "Précisez le moyen de paiement « autre ».");
        }
        MotifEcart motif = null;
        String precisionMotif = null;
        if (r.montantRecu() < total) {
            motif = r.motifEcart();
            precisionMotif = nettoyer(r.motifEcartPrecision());
            if (motif == null) {
                throw new RegleMetierException("MOTIF_ECART_REQUIS",
                        "Le montant encaissé est inférieur au total : indiquez le motif de l'écart.");
            }
            if (motif == MotifEcart.AUTRE && precisionMotif == null) {
                throw new RegleMetierException("MOTIF_ECART_A_PRECISER", "Précisez le motif « autre » de l'écart.");
            }
        }
        return new Encaissement(r.montantRecu(), r.moyen(), precisionMoyen, nettoyer(r.encaissePar()), motif,
                precisionMotif);
    }

    private void enregistrer(Commande commande, Encaissement e, String auteur, Long auteurId, long ecart) {
        jdbc.sql("""
                        insert into encaissement (commande_id, montant, moyen, moyen_precision, motif_ecart,
                            motif_ecart_precision, encaisse_par_id, encaisse_par_nom, date_encaissement)
                        values (:commande, :montant, :moyen, :moyenPrecision, :motif, :motifPrecision, :auteurId,
                            :nom, :date)
                        """)
                .param("commande", commande.getId()).param("montant", e.montant()).param("moyen", e.moyen().name())
                .param("moyenPrecision", e.precisionMoyen())
                .param("motif", ecart > 0 ? e.motif().name() : null).param("motifPrecision", e.precisionMotif())
                .param("auteurId", auteurId).param("nom", e.encaissePar() != null ? e.encaissePar() : auteur)
                .param("date", java.sql.Timestamp.from(horloge.instant())).update();
    }

    private static String nettoyer(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.strip();
    }
}
