package sn.boutique.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Lance le chargement des données de démonstration au démarrage (profil « demo » uniquement).
 * Exemple : {@code SPRING_PROFILES_ACTIVE=dev,demo ./mvnw spring-boot:run}.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class ChargeurDonneesDemo implements ApplicationRunner {

    private final ImportDonneesDemo importDonneesDemo;
    private final CommandesDemoClient commandesDemoClient;

    @Override
    public void run(ApplicationArguments arguments) {
        importDonneesDemo.charger();
        // Après la validation de l'import : les commandes ont besoin du catalogue et du compte déjà enregistrés
        try {
            commandesDemoClient.creer();
        } catch (RuntimeException e) {
            // Des commandes d'exemple manquantes ne doivent pas empêcher le site de démarrer
            log.error("Commandes de démonstration du client non créées : {}", e.getMessage(), e);
        }
    }
}
