package sn.boutique.commande.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clés d'idempotence de {@code POST /api/commandes} (en-tête {@code Idempotency-Key}).
 * <p>
 * Le frontend génère une clé par tentative de commande et la garde jusqu'au succès : coupure réseau, double appui
 * ou rechargement ne créent jamais deux commandes. La clé est liée au client (compte, ou panier invité) et
 * expire après {@code boutique.commande.duree-cle-idempotence}.
 * <p>
 * <strong>Deux requêtes simultanées avec la même clé</strong> : l'{@code INSERT ... ON CONFLICT DO NOTHING}
 * de la seconde attend la fin de la transaction de la première (index unique). Si la première valide, la seconde
 * ne crée rien et relit la commande ; si elle échoue (stock insuffisant…), la seconde prend sa place et réessaie
 * pour son propre compte.
 */
@Service
@RequiredArgsConstructor
public class CleIdempotenceService {

    private final JdbcClient jdbc;
    private final ProprietesCommande proprietes;
    private final Clock horloge;

    /**
     * @param nouvelle    la clé vient d'être enregistrée par cet appel : créer la commande
     * @param commandeId  commande déjà créée avec cette clé (si {@code nouvelle} est faux)
     * @param empreinte   empreinte de la demande qui a enregistré la clé
     */
    public record Etat(boolean nouvelle, Long commandeId, String empreinte) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Etat reserver(String portee, String cle, String empreinte) {
        OffsetDateTime maintenant = OffsetDateTime.ofInstant(horloge.instant(), ZoneOffset.UTC);
        // Une clé périmée est oubliée (la purge nocturne fait le reste)
        jdbc.sql("delete from cle_idempotence where portee = :portee and cle = :cle and expire_le <= :maintenant")
                .param("portee", portee).param("cle", cle).param("maintenant", maintenant).update();
        int inseree = jdbc.sql("""
                        insert into cle_idempotence (portee, cle, empreinte, date_creation, expire_le)
                        values (:portee, :cle, :empreinte, :maintenant, :expiration)
                        on conflict (portee, cle) do nothing
                        """)
                .param("portee", portee).param("cle", cle).param("empreinte", empreinte)
                .param("maintenant", maintenant)
                .param("expiration", maintenant.plus(proprietes.dureeCleIdempotence()))
                .update();
        if (inseree == 1) {
            return new Etat(true, null, empreinte);
        }
        return jdbc.sql("select commande_id, empreinte from cle_idempotence where portee = :portee and cle = :cle")
                .param("portee", portee).param("cle", cle)
                .query((rs, i) -> new Etat(false, rs.getObject("commande_id", Long.class), rs.getString("empreinte")))
                .single();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lier(String portee, String cle, long commandeId) {
        jdbc.sql("update cle_idempotence set commande_id = :commande where portee = :portee and cle = :cle")
                .param("commande", commandeId).param("portee", portee).param("cle", cle).update();
    }

    /** Purge des clés périmées. */
    @Transactional
    public int purger() {
        return jdbc.sql("delete from cle_idempotence where expire_le <= :maintenant")
                .param("maintenant", OffsetDateTime.ofInstant(horloge.instant(), ZoneOffset.UTC)).update();
    }
}
