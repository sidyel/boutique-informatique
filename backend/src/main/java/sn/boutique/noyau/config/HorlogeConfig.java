package sn.boutique.noyau.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Horloge unique de l'application (règle de CLAUDE.md) : tout le code lit l'heure avec
 * {@code horloge.instant()}, jamais {@code Instant.now()}, y compris l'audit JPA (voir AuditConfig).
 * <p>
 * UTC, tronquée à la microseconde : c'est la précision de {@code timestamptz} dans PostgreSQL. Une date
 * relue en base est ainsi égale à celle qui a été écrite (sous Linux, l'horloge système donne des
 * nanosecondes, que PostgreSQL perdrait). Les tests la remplacent par {@code HorlogeReglable}.
 */
@Configuration
public class HorlogeConfig {

    public static final Duration PRECISION = Duration.ofNanos(1_000);

    @Bean
    public Clock horloge() {
        return Clock.tick(Clock.systemUTC(), PRECISION);
    }
}
