package sn.boutique;

import org.springframework.boot.SpringApplication;

/**
 * Lance l'application en local avec PostgreSQL et Redis dans des conteneurs jetables
 * (pratique sans docker-compose) : exécuter cette classe depuis l'IDE.
 */
public class LancementLocalAvecConteneurs {

    public static void main(String[] args) {
        SpringApplication.from(BoutiqueApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
