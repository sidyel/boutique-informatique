package sn.boutique.commande.paiement;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.depot.PaiementDepot;
import sn.boutique.commande.facture.EmissionFactureService;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.HistoriqueStatutCommande;
import sn.boutique.commande.modele.Paiement;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.commande.modele.StatutPaiement;
import sn.boutique.marketing.service.ReservationCodePromo;
import sn.boutique.noyau.alerte.AlerteAdminService;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;
import sn.boutique.noyau.erreur.StockInsuffisantException;
import sn.boutique.stock.service.StockService;

/**
 * Enregistrement d'un paiement <strong>déjà vérifié auprès du prestataire</strong> (voir
 * {@link ConfirmationPaiementService}, qui appelle le prestataire hors transaction avant de venir ici ; rien d'autre
 * ne marque une commande payée).
 * <p>
 * La commande est verrouillée : deux paiements reçus en même temps pour la même commande passent l'un après
 * l'autre. Selon l'état de la commande :
 * <ul>
 *   <li><strong>en attente de paiement</strong> : elle devient {@code PAYEE} ;</li>
 *   <li><strong>annulée par l'expiration</strong> (paiement tardif) : si le stock le permet, elle est
 *   <strong>réactivée</strong> (stock réservé de nouveau) et devient {@code PAYEE} ; sinon le paiement est « à
 *   rembourser », l'équipe est alertée et le client informé ;</li>
 *   <li><strong>déjà réglée</strong> (seconde facture payée), ou annulée par une personne : le paiement est « à
 *   rembourser », sans jamais toucher à la commande, avec alerte et information du client.</li>
 * </ul>
 * Cette classe ne fait aucun appel réseau : elle reste dans des transactions courtes.
 */
@Slf4j
@Service
public class ReceptionPaiementService {

    public static final String AUTEUR_PAIEMENT = "PAIEMENT";
    private static final String AUTEUR_SYSTEME = "SYSTEME";

    private final CommandeDepot commandeDepot;
    private final PaiementDepot paiementDepot;
    private final StockService stockService;
    private final ReservationCodePromo reservationCodePromo;
    private final AlerteAdminService alertes;
    private final ApplicationEventPublisher evenements;
    private final TransactionTemplate transaction;
    private final EmissionFactureService emissionFacture;

    public ReceptionPaiementService(CommandeDepot commandeDepot, PaiementDepot paiementDepot,
                                    StockService stockService, ReservationCodePromo reservationCodePromo,
                                    AlerteAdminService alertes, ApplicationEventPublisher evenements,
                                    PlatformTransactionManager gestionnaireTransactions,
                                    EmissionFactureService emissionFacture) {
        this.emissionFacture = emissionFacture;
        this.commandeDepot = commandeDepot;
        this.paiementDepot = paiementDepot;
        this.stockService = stockService;
        this.reservationCodePromo = reservationCodePromo;
        this.alertes = alertes;
        this.evenements = evenements;
        this.transaction = new TransactionTemplate(gestionnaireTransactions);
    }

    /** Issue d'un paiement reçu. */
    public enum Issue {
        /** La commande est réglée par ce paiement. */
        COMMANDE_REGLEE,
        /** Paiement tardif : la commande annulée par l'expiration est réactivée, le stock est de nouveau réservé. */
        COMMANDE_REACTIVEE,
        /** Paiement sans vente à laquelle le rattacher : à rembourser. */
        A_REMBOURSER,
        /** Paiement déjà enregistré (notification reçue deux fois) : rien à faire. */
        DEJA_ENREGISTRE
    }

    /**
     * Un paiement ne pouvant pas être honoré : le client est informé après le commit (e-mail s'il en a donné un,
     * message au téléphone).
     */
    public record PaiementARembourser(long commandeId, long montant) {
    }

    public Issue enregistrerPaiementRecu(long paiementId) {
        try {
            return transaction.execute(statut -> traiter(paiementId, true));
        } catch (StockInsuffisantException e) {
            // La réactivation a échoué faute de stock : sa transaction est annulée en entier (rien n'a changé),
            // le paiement est traité à part comme un paiement à rembourser
            return transaction.execute(statut -> traiter(paiementId, false));
        }
    }

    /** Un prestataire dit qu'une facture a échoué ou été annulée (jamais pour une facture déjà payée). */
    public void enregistrerEchec(long paiementId, StatutPaiement statutFinal) {
        transaction.executeWithoutResult(statut -> {
            String numero = paiementDepot.numeroCommande(paiementId).orElse(null);
            if (numero == null) {
                return;
            }
            commandeDepot.findByNumeroPourMiseAJour(numero);
            paiementDepot.findById(paiementId).filter(p -> p.getStatut() == StatutPaiement.EN_ATTENTE)
                    .ifPresent(p -> p.setStatut(statutFinal));
        });
    }

    private Issue traiter(long paiementId, boolean reactivationPossible) {
        String numero = paiementDepot.numeroCommande(paiementId)
                .orElseThrow(() -> new RessourceIntrouvableException("Paiement", paiementId));
        Commande commande = commandeDepot.findByNumeroPourMiseAJour(numero)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", numero));
        // Le paiement n'est lu qu'une fois la commande verrouillée : une notification concurrente l'a peut-être déjà traité
        Paiement paiement = paiementDepot.findById(paiementId)
                .orElseThrow(() -> new RessourceIntrouvableException("Paiement", paiementId));
        if (paiement.getStatut() == StatutPaiement.REUSSI || paiement.getStatut() == StatutPaiement.A_REMBOURSER
                || paiement.getStatut() == StatutPaiement.REMBOURSE) {
            return Issue.DEJA_ENREGISTRE;
        }
        if (commande.getStatut() == StatutCommande.EN_ATTENTE_PAIEMENT) {
            paiement.setStatut(StatutPaiement.REUSSI);
            commande.changerStatut(StatutCommande.PAYEE, AUTEUR_PAIEMENT, "Paiement reçu");
            commande.setExpireLe(null);
            // Même transaction : jamais de commande payée sans facture, jamais de numéro perdu
            emissionFacture.emettre(commande, null);
            log.info("Commande {} réglée", commande.getNumero());
            return Issue.COMMANDE_REGLEE;
        }
        if (reactivationPossible && commande.getStatut() == StatutCommande.ANNULEE && annuleeParExpiration(commande)) {
            // Lève StockInsuffisantException si une ligne n'est plus disponible : tout est annulé, voir plus haut
            stockService.reserver(commande);
            if (commande.getCodePromo() != null && !reservationCodePromo.reserver(commande.getCodePromo())) {
                log.warn("Code promo {} épuisé entre-temps : commande {} réactivée avec la remise déjà accordée",
                        commande.getCodePromo(), commande.getNumero());
            }
            paiement.setStatut(StatutPaiement.REUSSI);
            commande.changerStatut(StatutCommande.PAYEE, AUTEUR_PAIEMENT,
                    "Paiement reçu après l'expiration : commande réactivée, stock réservé de nouveau");
            commande.setExpireLe(null);
            emissionFacture.emettre(commande, null);
            log.info("Commande {} réactivée par un paiement tardif", commande.getNumero());
            return Issue.COMMANDE_REACTIVEE;
        }
        // Commande déjà réglée, annulée par une personne, ou sans stock : l'argent est là sans vente
        paiement.setStatut(StatutPaiement.A_REMBOURSER);
        alertes.signaler("Paiement à rembourser : commande " + commande.getNumero(),
                "Un paiement de %d FCFA (référence %s) est arrivé pour la commande %s, dont le statut est %s. Il est marqué « à rembourser »."
                        .formatted(paiement.getMontant(), paiement.getReferenceExterne(), commande.getNumero(),
                                commande.getStatut()));
        evenements.publishEvent(new PaiementARembourser(commande.getId(), paiement.getMontant()));
        return Issue.A_REMBOURSER;
    }

    /** La dernière annulation vient de la tâche d'expiration (réservation échue), pas d'une décision humaine. */
    private static boolean annuleeParExpiration(Commande commande) {
        List<HistoriqueStatutCommande> historique = commande.getHistorique();
        return !historique.isEmpty() && AUTEUR_SYSTEME.equals(historique.getLast().getAuteur())
                && historique.getLast().getNouveauStatut() == StatutCommande.ANNULEE;
    }
}
