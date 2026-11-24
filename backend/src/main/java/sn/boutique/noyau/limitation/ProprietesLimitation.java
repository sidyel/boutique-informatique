package sn.boutique.noyau.limitation;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limites de débit des routes d'authentification (préfixe {@code boutique.limitation}), ADR 0003 §9.
 * Par IP : valeurs larges (CGNAT). Par e-mail : valeurs strictes (attaque ciblée sur un compte).
 *
 * @param actif interrupteur général (désactivable en cas d'incident Redis prolongé)
 */
@Validated
@ConfigurationProperties(prefix = "boutique.limitation")
public record ProprietesLimitation(
        boolean actif,
        @Valid @NotNull Regle connexionIp,
        @Valid @NotNull Regle connexionEmail,
        @Valid @NotNull Regle inscriptionIp,
        @Valid @NotNull Regle motDePasseOublieIp,
        @Valid @NotNull Regle motDePasseOublieEmail,
        @Valid @NotNull Regle renvoiVerificationIp,
        @Valid @NotNull Regle renvoiVerificationEmail,
        @Valid @NotNull Regle rafraichirIp) {

    /**
     * @param limite  nombre de requêtes autorisées dans la fenêtre
     * @param fenetre durée de la fenêtre (fixe : le compteur repart à zéro à son expiration)
     */
    public record Regle(@Min(1) int limite, @NotNull Duration fenetre) {
    }
}
