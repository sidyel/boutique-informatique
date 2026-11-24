package sn.boutique.noyau.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active les tâches planifiées ({@code @Scheduled}). Désactivable avec
 * {@code boutique.planification.active=false} (tests, instance dédiée aux requêtes).
 * <p>
 * Plusieurs instances du backend exécuteraient chacune les tâches : celles d'aujourd'hui sont
 * idempotentes (purges). Une tâche qui ne doit tourner qu'une fois demandera un verrou (ShedLock).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "boutique.planification", name = "active", havingValue = "true", matchIfMissing = true)
public class PlanificationConfig {
}
