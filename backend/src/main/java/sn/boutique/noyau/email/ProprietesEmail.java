package sn.boutique.noyau.email;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * E-mails transactionnels (préfixe {@code boutique.email}).
 *
 * @param expediteur adresse « De », par exemple {@code Boutique <no-reply@boutique.sn>} (EMAIL_EXPEDITEUR)
 * @param nomSite    nom affiché dans les e-mails
 * @param tentatives nombre d'essais d'envoi avant abandon (serveur SMTP momentanément indisponible)
 */
@Validated
@ConfigurationProperties(prefix = "boutique.email")
public record ProprietesEmail(@NotBlank String expediteur, @NotBlank String nomSite, @Min(1) int tentatives) {
}
