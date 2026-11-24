package sn.boutique.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Lance le chargement des données de démonstration au démarrage (profil « demo » uniquement).
 * Exemple : {@code SPRING_PROFILES_ACTIVE=dev,demo ./mvnw spring-boot:run}.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class ChargeurDonneesDemo implements ApplicationRunner {

    private final ImportDonneesDemo importDonneesDemo;

    @Override
    public void run(ApplicationArguments arguments) {
        importDonneesDemo.charger();
    }
}
