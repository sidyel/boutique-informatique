package sn.boutique.noyau.config;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propriétés propres à l'application (préfixe {@code boutique}).
 *
 * @param urlSite        URL publique du site (canoniques, liens des e-mails, flux produits)
 * @param corsOrigines   origines autorisées à appeler l'API depuis un navigateur
 * @param indexationAutorisee vrai uniquement en production : faux, robots.txt renvoie « Disallow: / » et
 *                       toutes les pages reçoivent « noindex » (préproduction, démonstration ; ADR 0007)
 */
@Validated
@ConfigurationProperties(prefix = "boutique")
public record ProprietesBoutique(
        @NotBlank String urlSite,
        @NotEmpty List<String> corsOrigines,
        boolean indexationAutorisee) {

    /** URL publique sans barre oblique finale : « https://boutique.sn ». */
    public String urlSiteSansBarre() {
        return urlSite.endsWith("/") ? urlSite.substring(0, urlSite.length() - 1) : urlSite;
    }
}
