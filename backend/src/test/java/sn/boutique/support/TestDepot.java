package sn.boutique.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import sn.boutique.noyau.config.HorlogeConfig;
import sn.boutique.TestcontainersConfiguration;
import sn.boutique.noyau.audit.AuditConfig;

/**
 * Test de dépôt JPA sur un vrai PostgreSQL (Testcontainers), migrations Flyway appliquées.
 * Chaque test tourne dans une transaction annulée à la fin.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, AuditConfig.class, HorlogeConfig.class})
public @interface TestDepot {
}
