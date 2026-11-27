package sn.boutique.commande.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.commande.paiement.ConfirmationPaiementService;
import sn.boutique.commande.paiement.PaiementIndisponibleException;

/**
 * Libère le stock des commandes dont la réservation est arrivée à terme :
 * <ul>
 *   <li>paiement en ligne non réglé après {@code delaiPaiementMobileMinutes} (30 min par défaut) ;</li>
 *   <li>paiement à la livraison non confirmé par le service client après
 *       {@code paiementLivraisonDelaiAnnulationHeures} (24 h par défaut), pour ne pas bloquer le stock.</li>
 * </ul>
 * <strong>Plusieurs instances</strong> : chaque commande est traitée dans sa propre transaction, après
 * {@code SELECT ... FOR UPDATE} puis revérification du statut et de l'échéance. Si une autre instance (ou une
 * confirmation du service client) est passée avant, la commande n'est plus en attente : rien n'est refait, le stock
 * n'est jamais libéré deux fois. Une commande qui échoue n'empêche pas les suivantes.
 */
@Slf4j
@Component
public class ExpirationCommandes {

    /** Commandes traitées par passage ; le reste attend le passage suivant (une minute plus tard). */
    private static final int LOT = 100;

    private final JdbcClient jdbc;
    private final CommandeDepot commandeDepot;
    private final GestionCommandeService gestion;
    private final CleIdempotenceService cles;
    private final ConfirmationPaiementService confirmation;
    private final ProprietesCommande proprietes;
    private final Clock horloge;
    private final TransactionTemplate transaction;

    public ExpirationCommandes(JdbcClient jdbc, CommandeDepot commandeDepot, GestionCommandeService gestion,
                               CleIdempotenceService cles, ConfirmationPaiementService confirmation,
                               ProprietesCommande proprietes, Clock horloge,
                               PlatformTransactionManager gestionnaireTransactions) {
        this.jdbc = jdbc;
        this.commandeDepot = commandeDepot;
        this.gestion = gestion;
        this.cles = cles;
        this.confirmation = confirmation;
        this.proprietes = proprietes;
        this.horloge = horloge;
        this.transaction = new TransactionTemplate(gestionnaireTransactions);
    }

    /** @return nombre de commandes annulées */
    @Scheduled(fixedDelayString = "${boutique.planification.expiration-commandes:PT1M}",
            initialDelayString = "${boutique.planification.expiration-commandes:PT1M}")
    public int expirer() {
        Instant maintenant = horloge.instant();
        List<EcheanceCommande> echeances = jdbc.sql("""
                        select numero, statut, expire_le from commande
                         where statut in ('EN_ATTENTE_PAIEMENT', 'EN_ATTENTE_CONFIRMATION') and expire_le <= :maintenant
                         order by expire_le
                         limit :lot
                        """)
                .param("maintenant", OffsetDateTime.ofInstant(maintenant, ZoneOffset.UTC))
                .param("lot", LOT)
                .query((rs, i) -> new EcheanceCommande(rs.getString(1), rs.getString(2),
                        rs.getObject(3, OffsetDateTime.class).toInstant()))
                .list();
        int annulees = 0;
        for (EcheanceCommande echeance : echeances) {
            String numero = echeance.numero();
            try {
                if (!paiementVerifie(echeance, maintenant)) {
                    continue;
                }
                if (Boolean.TRUE.equals(transaction.execute(statut -> expirer(numero, maintenant)))) {
                    annulees++;
                }
            } catch (RuntimeException e) {
                log.error("Expiration de la commande {} impossible : elle sera retentée au prochain passage", numero, e);
            }
        }
        if (annulees > 0) {
            log.info("Expiration : {} commande(s) annulée(s), stock remis en vente", annulees);
        }
        return annulees;
    }

    private record EcheanceCommande(String numero, String statut, Instant expireLe) {
    }

    /**
     * Avant d'annuler une commande dont le paiement en ligne n'est pas arrivé, on demande au prestataire si le client
     * n'a pas payé quand même (notification perdue, client revenu trop tard sur le site). L'appel réseau se fait
     * <strong>hors transaction</strong> ; la transaction d'annulation, qui suit, revérifie le statut sous verrou.
     *
     * @return faux si l'annulation doit attendre le prochain passage (prestataire injoignable, délai de grâce non écoulé)
     */
    private boolean paiementVerifie(EcheanceCommande echeance, Instant maintenant) {
        if (!"EN_ATTENTE_PAIEMENT".equals(echeance.statut())) {
            return true;
        }
        try {
            confirmation.confirmerCommande(echeance.numero());
            return true;
        } catch (PaiementIndisponibleException e) {
            boolean graceEcoulee = !maintenant.isBefore(echeance.expireLe().plus(proprietes.graceVerificationPaiement()));
            log.warn("Paiement de la commande {} non vérifiable ({}) : {}", echeance.numero(), e.getMessage(),
                    graceEcoulee ? "délai de grâce écoulé, annulation" : "annulation reportée");
            return graceEcoulee;
        } catch (RuntimeException e) {
            log.error("Vérification du paiement de la commande {} impossible, annulation reportée", echeance.numero(), e);
            return false;
        }
    }

    private boolean expirer(String numero, Instant maintenant) {
        Commande commande = commandeDepot.findByNumeroPourMiseAJour(numero).orElse(null);
        if (commande == null || !commande.enAttente() || commande.getExpireLe() == null
                || commande.getExpireLe().isAfter(maintenant)) {
            return false;
        }
        String motif = commande.getStatut() == StatutCommande.EN_ATTENTE_PAIEMENT
                ? "Paiement non reçu dans le délai : réservation expirée"
                : "Commande non confirmée par téléphone dans le délai : réservation expirée";
        gestion.annulerVerrouillee(commande, GestionCommandeService.AUTEUR_SYSTEME, motif);
        return true;
    }

    /** Chaque nuit à 3 h 50, heure de Dakar : clés d'idempotence périmées. */
    @Scheduled(cron = "${boutique.planification.purge-commandes-cron:0 50 3 * * *}", zone = "Africa/Dakar")
    public void purger() {
        int supprimees = cles.purger();
        log.info("Purge des clés d'idempotence périmées : {} supprimée(s)", supprimees);
    }
}
