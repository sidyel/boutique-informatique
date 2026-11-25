package sn.boutique.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/** Signature d'envoi et URL de diffusion des vidéos, calculées sans appel réseau (clé fictive). */
class SignatureCloudinaryTest {

    private static final String SECRET = "secretDeTestQuiNeDoitJamaisSortir";

    private final StockageCloudinary stockage = new StockageCloudinary(avecCle(
            "cloudinary://123456789012345:" + SECRET + "@nuage-test"));

    @Test
    void laSignatureNeCouvreQueCetIdPublicEnVideoSansEcrasement() throws Exception {
        String idPublic = "dev/boutique/produits/42/videos/vivobook-15-k3f9q2mz";
        Instant horodatage = Instant.parse("2026-11-25T09:30:00Z");

        StockageMedia.SignatureEnvoi envoi = stockage.signerEnvoiVideo(idPublic, horodatage);

        assertThat(envoi.urlEnvoi()).isEqualTo("https://api.cloudinary.com/v1_1/nuage-test/video/upload");
        Map<String, String> parametres = envoi.parametres();
        assertThat(parametres).containsEntry("public_id", idPublic)
                .containsEntry("asset_folder", "dev/boutique/produits/42/videos")
                .containsEntry("overwrite", "false")
                .containsEntry("allowed_formats", "mp4,webm")
                .containsEntry("timestamp", String.valueOf(horodatage.getEpochSecond()))
                .containsEntry("api_key", "123456789012345");
        assertThat(parametres.values()).noneMatch(valeur -> valeur.contains(SECRET));

        // Calcul indépendant : SHA-1 des paramètres signés triés « cle=valeur&… » suivis du secret
        Map<String, String> signes = new TreeMap<>(parametres);
        signes.remove("api_key");
        signes.remove("signature");
        String aSigner = signes.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&")) + SECRET;
        String attendue = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest(aSigner.getBytes(StandardCharsets.UTF_8)));
        assertThat(parametres.get("signature")).isEqualTo(attendue);
    }

    @Test
    void laVideoEtSaMiniatureSontServiesEnUrlsTransformees() {
        StockageMedia.MediaStocke video = new StockageMedia.MediaStocke(
                "dev/boutique/produits/42/videos/vivobook-15-k3f9q2mz", 1732520000L, "mp4", 1920, 1080, 50_000_000L);

        assertThat(stockage.urlVideo(video)).isEqualTo("https://res.cloudinary.com/nuage-test/video/upload/f_auto,q_auto"
                + "/v1732520000/dev/boutique/produits/42/videos/vivobook-15-k3f9q2mz.mp4");
        assertThat(stockage.urlMiniatureVideo(video)).isEqualTo("https://res.cloudinary.com/nuage-test/video/upload/"
                + "so_auto,f_auto,q_auto/v1732520000/dev/boutique/produits/42/videos/vivobook-15-k3f9q2mz.jpg");
    }

    @Test
    void unSvgEstServiEnVectorielNettoyeEtJamaisConvertiParFAuto() {
        assertThat(StockageCloudinary.urlImage("nuage-test", new StockageMedia.MediaStocke(
                "dev/boutique/site/logo-k3f9q2mz", 12L, "svg", null, null, 300L)))
                .isEqualTo("https://res.cloudinary.com/nuage-test/image/upload/fl_sanitize/v12/dev/boutique/site/logo-k3f9q2mz.svg");
        assertThat(StockageCloudinary.urlImage("nuage-test", new StockageMedia.MediaStocke(
                "dev/boutique/produits/pc-k3f9q2mz", 12L, "png", 1000, 1000, 300L)))
                .contains("/image/upload/f_auto,q_auto/v12/");
    }

    private static ProprietesMedias avecCle(String url) {
        ProprietesMedias base = VerificationFichiersTest.proprietes(DataSize.ofMegabytes(5));
        return new ProprietesMedias(url, base.dossierRacine(), base.delai(), base.images(), base.svg(), base.videos(),
                base.suppression(), base.youtube());
    }
}
