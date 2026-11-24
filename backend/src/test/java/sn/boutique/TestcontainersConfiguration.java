package sn.boutique;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Conteneurs PostgreSQL, Redis et Mailpit partagés par les tests d'intégration.
 * Les versions sont alignées sur celles de infra/docker-compose.yml.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer conteneurPostgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    /** Serveur SMTP de test : les tests lisent les e-mails reçus avec {@code ClientMailpit}. */
    @Bean
    GenericContainer<?> conteneurMailpit() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.34")).withExposedPorts(1025, 8025);
    }

    @Bean
    DynamicPropertyRegistrar proprietesMailpit(GenericContainer<?> conteneurMailpit) {
        return registre -> {
            registre.add("spring.mail.host", conteneurMailpit::getHost);
            registre.add("spring.mail.port", () -> conteneurMailpit.getMappedPort(1025));
            registre.add("test.mailpit.url",
                    () -> "http://" + conteneurMailpit.getHost() + ":" + conteneurMailpit.getMappedPort(8025));
        };
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> conteneurRedis() {
        return new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379);
    }
}
