package sn.boutique.noyau.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Active le remplissage automatique de dateCreation et dateModification.
 */
@Configuration
@EnableJpaAuditing
public class AuditConfig {
}
