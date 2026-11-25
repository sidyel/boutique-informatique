package sn.boutique.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.util.unit.DataSize;
import sn.boutique.noyau.erreur.RegleMetierException;

class VerificationFichiersTest {

    private final VerificationFichiers verification = new VerificationFichiers(proprietes(DataSize.ofMegabytes(5)),
            new NettoyeurSvg());

    @Test
    void uneVraiePhotoJpegEstAccepteeAvecSesDimensions() {
        FichierVerifie fichier = verification.verifierImage(ImagesDeTest.image("jpg", 1200, 900), UsageImage.PRODUIT);

        assertThat(fichier.typeMime()).isEqualTo("image/jpeg");
        assertThat(fichier.extension()).isEqualTo("jpg");
        assertThat(fichier.largeur()).isEqualTo(1200);
        assertThat(fichier.hauteur()).isEqualTo(900);
    }

    @Test
    void lesDimensionsDuPngEtDuWebpSontLuesDansLesEntetes() {
        FichierVerifie png = verification.verifierImage(ImagesDeTest.image("png", 1000, 800), UsageImage.PRODUIT);
        assertThat(png.typeMime()).isEqualTo("image/png");
        assertThat(new int[] {png.largeur(), png.hauteur()}).containsExactly(1000, 800);

        FichierVerifie webp = verification.verifierImage(ImagesDeTest.enteteWebp(1600, 1200), UsageImage.PRODUIT);
        assertThat(webp.typeMime()).isEqualTo("image/webp");
        assertThat(new int[] {webp.largeur(), webp.hauteur()}).containsExactly(1600, 1200);

        FichierVerifie jpeg = verification.verifierImage(ImagesDeTest.enteteJpeg(2000, 1500), UsageImage.PRODUIT);
        assertThat(new int[] {jpeg.largeur(), jpeg.hauteur()}).containsExactly(2000, 1500);
    }

    @Test
    void uneBombeDeDecompressionEstRefuseeSansEtreDecodee() {
        byte[] bombe = ImagesDeTest.bombePng(9_000);
        assertThat(bombe.length).as("quelques dizaines de Ko pour 81 millions de pixels").isLessThan(200_000);
        Runtime jvm = Runtime.getRuntime();
        System.gc();
        long avant = jvm.totalMemory() - jvm.freeMemory();
        long debut = System.nanoTime();

        assertThatThrownBy(() -> verification.verifierImage(bombe, UsageImage.PRODUIT))
                .isInstanceOf(RegleMetierException.class)
                .hasFieldOrPropertyWithValue("code", "IMAGE_TROP_GRANDE")
                .hasMessageContaining("9000 × 9000");

        assertThat(Duration.ofNanos(System.nanoTime() - debut)).as("lecture des en-têtes seulement")
                .isLessThan(Duration.ofSeconds(2));
        long consommee = (jvm.totalMemory() - jvm.freeMemory()) - avant;
        assertThat(consommee).as("aucun décodage des 81 Mo de pixels").isLessThan(60L * 1024 * 1024);
    }

    @Test
    void lesEntetesJpegEtWebpGeantsSontRefuses() {
        assertThatThrownBy(() -> verification.verifierImage(ImagesDeTest.enteteJpeg(12_000, 900), UsageImage.PRODUIT))
                .hasFieldOrPropertyWithValue("code", "IMAGE_TROP_GRANDE");
        assertThatThrownBy(() -> verification.verifierImage(ImagesDeTest.enteteWebp(20_000, 20_000), UsageImage.BANNIERE))
                .hasFieldOrPropertyWithValue("code", "IMAGE_TROP_GRANDE");
        // 8 000 px exactement : accepté
        assertThat(verification.verifierImage(ImagesDeTest.enteteJpeg(8_000, 6_000), UsageImage.PRODUIT).largeur())
                .isEqualTo(8_000);
    }

    @Test
    void uneImageTropPetitePourUnePhotoProduitEstRefusee() {
        byte[] image = ImagesDeTest.image("png", 1200, 700);

        assertThatThrownBy(() -> verification.verifierImage(image, UsageImage.PRODUIT))
                .hasFieldOrPropertyWithValue("code", "IMAGE_TROP_PETITE")
                .hasMessageContaining("800 px");
        // Les bannières ont leur propre minimum (600 px)
        assertThat(verification.verifierImage(image, UsageImage.BANNIERE).hauteur()).isEqualTo(700);
    }

    @Test
    void leTypeEstDetecteSurLeContenuEtNonSurLeNom() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> verification.verifierImage(html, UsageImage.PRODUIT))
                .hasFieldOrPropertyWithValue("code", "TYPE_NON_AUTORISE");
        byte[] gif = ImagesDeTest.image("gif", 900, 900);
        assertThatThrownBy(() -> verification.verifierImage(gif, UsageImage.PRODUIT))
                .hasFieldOrPropertyWithValue("code", "TYPE_NON_AUTORISE");
        byte[] pdf = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> verification.verifierImage(pdf, UsageImage.PRODUIT))
                .hasFieldOrPropertyWithValue("code", "TYPE_NON_AUTORISE");
    }

    @Test
    void uneImageEndommageeEstRefusee() {
        byte[] png = ImagesDeTest.image("png", 1000, 1000);
        byte[] tronque = java.util.Arrays.copyOf(png, 20); // signature + début d'IHDR
        assertThatThrownBy(() -> verification.verifierImage(tronque, UsageImage.PRODUIT))
                .hasFieldOrPropertyWithValue("code", "IMAGE_ILLISIBLE");
        assertThatThrownBy(() -> verification.verifierImage(new byte[0], UsageImage.PRODUIT))
                .hasFieldOrPropertyWithValue("code", "FICHIER_VIDE");
    }

    @Test
    void unFichierTropLourdEstRefuse() {
        VerificationFichiers stricte = new VerificationFichiers(proprietes(DataSize.ofKilobytes(1)), new NettoyeurSvg());
        assertThatThrownBy(() -> stricte.verifierImage(ImagesDeTest.image("png", 1000, 1000), UsageImage.PRODUIT))
                .hasFieldOrPropertyWithValue("code", "FICHIER_TROP_LOURD");
    }

    @ParameterizedTest
    @EnumSource(value = UsageImage.class, names = "LOGO", mode = EnumSource.Mode.EXCLUDE)
    void leSvgEstRefuseHorsLogo(UsageImage usage) {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"10\" height=\"10\"/></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> verification.verifierImage(svg, usage))
                .hasFieldOrPropertyWithValue("code", "TYPE_NON_AUTORISE");
    }

    @Test
    void leSvgDuLogoEstNettoye() {
        byte[] svg = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                  <script>alert(1)</script><rect width="10" height="10" fill="#C2410C"/>
                </svg>""".getBytes(StandardCharsets.UTF_8);

        FichierVerifie fichier = verification.verifierImage(svg, UsageImage.LOGO);

        assertThat(fichier.estSvg()).isTrue();
        assertThat(new String(fichier.contenu(), StandardCharsets.UTF_8)).doesNotContain("script").contains("<rect");
    }

    static ProprietesMedias proprietes(DataSize tailleMax) {
        return new ProprietesMedias("", "dev/boutique", Duration.ofSeconds(30),
                new ProprietesMedias.Images(tailleMax, 8_000,
                        Map.of(UsageImage.PRODUIT, 800, UsageImage.BANNIERE, 600, UsageImage.PARTAGE, 315,
                                UsageImage.FAVICON, 512, UsageImage.LOGO, 16), 15),
                new ProprietesMedias.Svg(DataSize.ofKilobytes(512)),
                new ProprietesMedias.Videos(DataSize.ofMegabytes(100), 1, java.util.List.of("mp4", "webm"),
                        Duration.ofMinutes(50), Duration.ofHours(1)),
                new ProprietesMedias.Suppression(3, Duration.ZERO),
                new ProprietesMedias.Youtube("https://www.youtube.com/oembed", Duration.ofSeconds(3)));
    }
}
