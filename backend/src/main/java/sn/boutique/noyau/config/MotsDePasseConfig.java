package sn.boutique.noyau.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hachage des mots de passe : BCrypt, coût 12 (environ 250 ms par vérification sur un serveur
 * courant, ce qui ralentit fortement une attaque par force brute sans gêner la connexion).
 * BCrypt ne prend en compte que les 72 premiers octets : la validation des mots de passe limite
 * leur longueur en conséquence (phase 1, authentification).
 */
@Configuration
public class MotsDePasseConfig {

    public static final int COUT_BCRYPT = 12;

    @Bean
    public PasswordEncoder encodeurMotDePasse() {
        return new BCryptPasswordEncoder(COUT_BCRYPT);
    }
}
