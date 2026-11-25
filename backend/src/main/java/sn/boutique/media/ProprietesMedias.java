package sn.boutique.media;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
        @Valid @NotNull Videos videos,
        @Valid @NotNull Suppression suppression,
        @Valid @NotNull Youtube youtube) {

    /**
     * @param tailleMax poids maximal d'une image envoyée
     * @param coteMax   plus grand côté accepté, en pixels : au-delà, refus (protection contre les « bombes de
     *                  décompression », fichiers légers qui occupent des gigaoctets une fois décodés)
     * @param coteMin   plus petit côté minimal par usage (photo produit : 800 px, pour un zoom net et les
     *                  minimums de Google Merchant)
     * @param maxParProduit nombre maximal d'images d'un produit (au-delà : 422)
     */
    public record Images(@NotNull DataSize tailleMax, @Min(1) int coteMax, @NotNull Map<UsageImage, Integer> coteMin,
                         @Min(1) int maxParProduit) {

        public int coteMinPour(UsageImage usage) {
            return coteMin.getOrDefault(usage, 1);
        }
    }

    /** SVG acceptés pour le logo et le favicon uniquement, après nettoyage. */
    public record Svg(@NotNull DataSize tailleMax) {
    }

    /**
     * Suppression sur Cloudinary après la validation en base : {@code essais} tentatives espacées de
     * {@code pause} ; en cas d'échec, l'idPublic est enregistré dans {@code media_orphelin}.
     */
    public record Suppression(@Min(1) int essais, @NotNull Duration pause) {
    }

    /**
     * Vidéos envoyées directement à Cloudinary par le navigateur (envoi signé).
     *
     * @param validiteSignature durée pendant laquelle l'envoi peut commencer ; Cloudinary accepte une signature
     *                          pendant 1 h après son horodatage, que l'on antidate pour raccourcir ce délai
     * @param delaiDeclaration  délai supplémentaire, après l'expiration, pour déclarer la vidéo envoyée
     */
    public record Videos(@NotNull DataSize tailleMax, @Min(0) int maxParProduit, @NotEmpty List<String> formats,
                         @NotNull Duration validiteSignature, @NotNull Duration delaiDeclaration) {

        /** Fenêtre d'acceptation des signatures par Cloudinary. */
        public static final Duration FENETRE_CLOUDINARY = Duration.ofHours(1);

        @AssertTrue(message = "validite-signature doit être comprise entre 1 et 50 minutes (Cloudinary : 1 h au plus).")
        public boolean isValiditeSignatureAcceptee() {
            return validiteSignature != null && validiteSignature.compareTo(Duration.ofMinutes(1)) >= 0
                    && validiteSignature.compareTo(Duration.ofMinutes(50)) <= 0;
        }
    }

    /** Vérification des liens YouTube par oEmbed (délai court : une page d'administration attend la réponse). */
    public record Youtube(@NotBlank String urlOembed, @NotNull Duration delai) {
    }
}
