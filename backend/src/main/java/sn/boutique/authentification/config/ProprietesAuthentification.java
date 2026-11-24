package sn.boutique.authentification.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import sn.boutique.compte.modele.Role;

/**
 * Paramètres de l'authentification (préfixe {@code boutique.authentification}), voir ADR 0003.
 *
 * @param jwtSecret               clé HMAC en Base64 (variable JWT_SECRET), 32 octets minimum une fois décodée
 * @param secretEphemereAutorise  true en dev : sans JWT_SECRET, une clé aléatoire est générée au démarrage
 *                                (les sessions sont perdues à chaque redémarrage) ; false en production
 * @param emetteur                revendication « iss » des jetons d'accès, vérifiée au décodage
 * @param dureeJetonAcces         durée de vie du jeton d'accès (JWT)
 * @param dureeSessionClient      session d'un CLIENT, glissante (prolongée à chaque rafraîchissement)
 * @param dureeSessionInterne     session des rôles internes, absolue : le rafraîchissement ne la prolonge pas
 * @param echecsAvantVerrouillage nombre d'échecs de connexion consécutifs avant verrouillage du compte
 * @param dureeVerrouillage       durée du verrouillage
 * @param dureeJetonVerificationEmail  validité du lien de vérification de l'e-mail
 * @param dureeJetonReinitialisation   validité du lien de réinitialisation du mot de passe
 * @param cookie                  cookie du jeton de rafraîchissement
 */
@Validated
@ConfigurationProperties(prefix = "boutique.authentification")
public record ProprietesAuthentification(
        String jwtSecret,
        boolean secretEphemereAutorise,
        @NotBlank String emetteur,
        @NotNull Duration dureeJetonAcces,
        @NotNull Duration dureeSessionClient,
        @NotNull Duration dureeSessionInterne,
        @Min(1) int echecsAvantVerrouillage,
        @NotNull Duration dureeVerrouillage,
        @NotNull Duration dureeJetonVerificationEmail,
        @NotNull Duration dureeJetonReinitialisation,
        @Valid @NotNull Cookie cookie) {

    /**
     * @param nom    nom du cookie
     * @param chemin attribut Path : le cookie n'est envoyé qu'aux routes d'authentification
     * @param secure attribut Secure (false seulement en dev, pour tester en HTTP depuis un téléphone du réseau local)
     */
    public record Cookie(@NotBlank String nom, @NotBlank String chemin, boolean secure) {
    }

    public Duration dureeSession(Role role) {
        return role.estInterne() ? dureeSessionInterne : dureeSessionClient;
    }
}
