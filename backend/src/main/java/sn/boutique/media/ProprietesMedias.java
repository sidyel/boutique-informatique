package sn.boutique.media;

import java.time.Duration;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Médias (préfixe {@code boutique.medias}) : stockage Cloudinary et contrôles des fichiers envoyés.
 *
 * @param cloudinaryUrl  {@code cloudinary://cle:secret@nom-du-cloud} (variable CLOUDINARY_URL, jamais dans Git) ;
 *                       vide = stockage indisponible (l'application démarre, les envois échouent)
 * @param dossierRacine  dossier Cloudinary de l'environnement : {@code dev/boutique}, {@code boutique} en production
 * @param delai          délai maximal d'un appel à Cloudinary
 */
@Validated
@ConfigurationProperties(prefix = "boutique.medias")
public record ProprietesMedias(
        String cloudinaryUrl,
        @NotBlank @Pattern(regexp = "[a-z0-9_-]+(/[a-z0-9_-]+)*") String dossierRacine,
        @NotNull Duration delai,
        @Valid @NotNull Images images,
        @Valid @NotNull Svg svg,
        @Valid @NotNull Videos videos) {

    /**
     * @param tailleMax poids maximal d'une image envoyée
     * @param coteMax   plus grand côté accepté, en pixels : au-delà, refus (protection contre les « bombes de
     *                  décompression », fichiers légers qui occupent des gigaoctets une fois décodés)
     * @param coteMin   plus petit côté minimal par usage (photo produit : 800 px, pour un zoom net et les
     *                  minimums de Google Merchant)
     */
    public record Images(@NotNull DataSize tailleMax, @Min(1) int coteMax, @NotNull Map<UsageImage, Integer> coteMin) {

        public int coteMinPour(UsageImage usage) {
            return coteMin.getOrDefault(usage, 1);
        }
    }

    /** SVG acceptés pour le logo et le favicon uniquement, après nettoyage. */
    public record Svg(@NotNull DataSize tailleMax) {
    }

    /** Vidéos envoyées directement à Cloudinary par le navigateur (envoi signé). */
    public record Videos(@NotNull DataSize tailleMax, @Min(0) int maxParProduit) {
    }
}
