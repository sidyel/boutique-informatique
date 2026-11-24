package sn.boutique.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Horloge que les tests font avancer (expiration des jetons, fin de verrouillage…).
 * Importer {@link Configuration} : le bean remplace l'horloge système grâce à {@code @Primary}.
 */
public class HorlogeReglable extends Clock {

    private final AtomicReference<Instant> maintenant = new AtomicReference<>(Instant.now());

    public void reinitialiser() {
        maintenant.set(Instant.now());
    }

    public void avancer(Duration duree) {
        maintenant.updateAndGet(instant -> instant.plus(duree));
    }

    @Override
    public Instant instant() {
        return maintenant.get();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Configuration {

        @Bean
        @Primary
        HorlogeReglable horlogeReglable() {
            return new HorlogeReglable();
        }
    }
}
