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
    private final Clock horloge;
    private final TransactionTemplate transaction;

    public ExpirationCommandes(JdbcClient jdbc, CommandeDepot commandeDepot, GestionCommandeService gestion,
                               CleIdempotenceService cles, Clock horloge,
                               PlatformTransactionManager gestionnaireTransactions) {
        this.jdbc = jdbc;
        this.commandeDepot = commandeDepot;
        this.gestion = gestion;
        this.cles = cles;
        this.horloge = horloge;
        this.transaction = new TransactionTemplate(gestionnaireTransactions);
    }

    /** @return nombre de commandes annulées */
    @Scheduled(fixedDelayString = "${boutique.planification.expiration-commandes:PT1M}",
            initialDelayString = "${boutique.planification.expiration-commandes:PT1M}")
    public int expirer() {
        Instant maintenant = horloge.instant();
        List<String> numeros = jdbc.sql("""
                        select numero from commande
                         where statut in ('EN_ATTENTE_PAIEMENT', 'EN_ATTENTE_CONFIRMATION') and expire_le <= :maintenant
                         order by expire_le
                         limit :lot
                        """)
                .param("maintenant", OffsetDateTime.ofInstant(maintenant, ZoneOffset.UTC))
                .param("lot", LOT)
                .query(String.class)
                .list();
        int annulees = 0;
        for (String numero : numeros) {
            try {
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
