package sn.boutique.noyau.limitation;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limites de débit des routes d'authentification (préfixe {@code boutique.limitation}), ADR 0003 §9.
 * <ul>
 *   <li>par IP : valeurs larges (CGNAT) ;</li>
 *   <li>par couple (e-mail, IP) : valeurs strictes, réponse 429. Un attaquant n'épuise que SON quota : la
 *       victime qui se connecte depuis une autre IP n'est pas bloquée ;</li>
 *   <li>par e-mail seul : jamais de blocage de la connexion. {@code connexionEmailAlarme} déclenche une alerte
 *       (attaque ciblée depuis de nombreuses IP) ; pour les e-mails envoyés, au-delà de la limite, la réponse
 *       reste 202 mais aucun e-mail ne part (boîte de la victime protégée, rien n'est révélé).</li>
 * </ul>
 *
 * @param actif interrupteur général (désactivable en cas d'incident Redis prolongé)
 */
@Validated
@ConfigurationProperties(prefix = "boutique.limitation")
public record ProprietesLimitation(
        boolean actif,
        @Valid @NotNull Regle connexionIp,
        @Valid @NotNull Regle connexionEmailIp,
        @Valid @NotNull Regle connexionEmailAlarme,
        @Valid @NotNull Regle inscriptionIp,
        @Valid @NotNull Regle motDePasseOublieIp,
        @Valid @NotNull Regle motDePasseOublieEmailIp,
        @Valid @NotNull Regle motDePasseOublieEmail,
        @Valid @NotNull Regle renvoiVerificationIp,
        @Valid @NotNull Regle renvoiVerificationEmailIp,
        @Valid @NotNull Regle renvoiVerificationEmail,
        @Valid @NotNull Regle rafraichirIp) {

    /**
     * @param limite  nombre de requêtes autorisées dans la fenêtre
     * @param fenetre durée de la fenêtre (fixe : le compteur repart à zéro à son expiration)
     */
    public record Regle(@Min(1) int limite, @NotNull Duration fenetre) {
    }
}
