package sn.boutique.noyau.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Active {@code @Async} (envoi des e-mails après la réponse). L'exécuteur est celui configuré par
 * Spring Boot ({@code spring.task.execution.*}).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
