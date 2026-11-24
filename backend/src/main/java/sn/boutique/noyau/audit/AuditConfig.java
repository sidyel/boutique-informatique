package sn.boutique.noyau.audit;

import java.time.Clock;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Active le remplissage automatique de dateCreation et dateModification,
 * avec l'horloge de l'application (et non l'heure système).
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "fournisseurDateAudit")
public class AuditConfig {

    @Bean
    DateTimeProvider fournisseurDateAudit(Clock horloge) {
        return () -> Optional.of(horloge.instant());
    }
}
