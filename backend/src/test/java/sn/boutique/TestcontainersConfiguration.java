package sn.boutique;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import sn.boutique.support.CompteurRequetesSql;
import sn.boutique.support.FauxStockageMedia;
import sn.boutique.support.FauxVerificationYoutube;

/**
 * Conteneurs PostgreSQL, Redis et Mailpit partagés par les tests d'intégration.
 * Les versions sont alignées sur celles de infra/docker-compose.yml.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Remplace Cloudinary : aucun test n'envoie de fichier au vrai service. */
    @Bean
    @Primary
    FauxStockageMedia fauxStockageMedia() {
        return new FauxStockageMedia();
    }

    /** Remplace l'appel oEmbed à YouTube (l'implémentation réelle est testée contre un faux serveur HTTP). */
    @Bean
    @Primary
    FauxVerificationYoutube fauxVerificationYoutube() {
        return new FauxVerificationYoutube();
    }

    /**
     * La clé CLOUDINARY_URL du .env local est ignorée par les tests : même un appel direct à
     * StockageCloudinary échouerait au lieu de joindre le compte réel.
     */
    @Bean
    DynamicPropertyRegistrar sansCleCloudinary() {
        return registre -> {
            registre.add("boutique.medias.cloudinary-url", () -> "");
            // Pas d'attente entre deux essais de suppression dans les tests
            registre.add("boutique.medias.suppression.pause", () -> "0ms");
        };
    }

    /** Compte les requêtes SQL (détection des N+1), dans tous les contextes de test. */
    @Bean
    static CompteurRequetesSql compteurRequetesSql() {
        return new CompteurRequetesSql();
    }

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

    /** Instance Redis du cache, distincte de l'instance principale comme en production. */
    @Bean
    GenericContainer<?> conteneurRedisCache() {
        return new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
                .withCommand("redis-server", "--maxmemory", "64mb", "--maxmemory-policy", "allkeys-lru")
                .withExposedPorts(6379);
    }

    @Bean
    DynamicPropertyRegistrar proprietesRedisCache(GenericContainer<?> conteneurRedisCache) {
        return registre -> {
            registre.add("boutique.cache.redis.hote", conteneurRedisCache::getHost);
            registre.add("boutique.cache.redis.port", () -> conteneurRedisCache.getMappedPort(6379));
            registre.add("boutique.cache.redis.mot-de-passe", () -> "");
            // Court : le test du cache arrêté attend ce délai à chaque opération
            registre.add("boutique.cache.redis.delai", () -> "300ms");
        };
    }
}
