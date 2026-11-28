package sn.boutique.administration.journal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import sn.boutique.authentification.config.ProprietesSecondFacteur;

/**
 * Purge nocturne du journal : seules les lignes de plus de {@code conservation-journal} (3 ans) disparaissent. Le déclencheur
 * de la table refuse de toute façon de supprimer une ligne plus récente que 3 ans (ADR 0009 §9).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgeJournalAdministration {

    private final JdbcClient jdbc;
    private final ProprietesSecondFacteur proprietes;
    private final Clock horloge;

    /** Chaque nuit à 4 h, heure de Dakar. */
    @Scheduled(cron = "${boutique.second-facteur.purge-journal-cron:0 0 4 * * *}", zone = "Africa/Dakar")
    public int purger() {
        Instant limite = horloge.instant().minus(proprietes.conservationJournal());
        int lignes = jdbc.sql("delete from journal_administration where date_action < :limite")
                .param("limite", Timestamp.from(limite)).update();
        log.info("Purge du journal d'administration avant {} : {} ligne(s) supprimée(s)", limite, lignes);
        return lignes;
    }
}
