package sn.boutique.panier.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supprime chaque nuit les paniers invités abandonnés : ni le panier ni aucune de ses lignes n'a été modifié
 * depuis {@code boutique.panier.conservation-invite} (30 jours, la durée du cookie). Les lignes partent avec
 * (ON DELETE CASCADE). Les paniers des clients connectés sont gardés.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurgePaniersInvites {

    private final JdbcClient jdbc;
    private final ProprietesPanier proprietes;
    private final Clock horloge;

    /** Chaque nuit à 3 h 45, heure de Dakar. */
    @Scheduled(cron = "${boutique.panier.purge-cron:0 45 3 * * *}", zone = "Africa/Dakar")
    @Transactional
    public int purger() {
        Instant limite = horloge.instant().minus(proprietes.conservationInvite());
        int supprimes = jdbc.sql("""
                        delete from panier p
                         where p.jeton_invite is not null
                           and p.date_modification < :limite
                           and not exists (select 1 from ligne_panier l
                                            where l.panier_id = p.id and l.date_modification >= :limite)
                        """)
                .param("limite", OffsetDateTime.ofInstant(limite, ZoneOffset.UTC))
                .update();
        log.info("Purge des paniers invités sans activité depuis le {} : {} supprimé(s)", limite, supprimes);
        return supprimes;
    }
}
