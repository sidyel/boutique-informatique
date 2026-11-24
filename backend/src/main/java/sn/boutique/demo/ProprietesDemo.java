package sn.boutique.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration du profil « demo » (voir application-demo.yml).
 *
 * @param adminEmail       e-mail du compte SUPER_ADMIN de démonstration
 * @param adminMotDePasse  son mot de passe (variable DEMO_ADMIN_MOT_DE_PASSE) ; vide = pas de compte admin
 */
@ConfigurationProperties(prefix = "boutique.demo")
public record ProprietesDemo(String adminEmail, String adminMotDePasse) {
}
