package sn.boutique.noyau.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Horloge injectable : le code métier appelle {@code horloge.instant()} plutôt que {@code Instant.now()},
 * ce qui permet aux tests de fixer l'heure (expiration des jetons, codes promo, verrouillage…).
 */
@Configuration
public class HorlogeConfig {

    @Bean
    public Clock horloge() {
        return Clock.systemUTC();
    }
}
