package sn.boutique.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransformationsImagesTest {

    private static final String ENREGISTREE =
            "https://res.cloudinary.com/nuage/image/upload/f_auto,q_auto/v1732520000/boutique/site/favicon-k3f9q2mz.png";

    @Test
    void lesFaviconsRemplacentLaTransformationSansLEmpiler() {
        assertThat(TransformationsImages.favicon(ENREGISTREE, 32)).isEqualTo(
                "https://res.cloudinary.com/nuage/image/upload/c_fill,w_32,h_32,f_png,q_auto/v1732520000/boutique/site/favicon-k3f9q2mz.png");
        assertThat(TransformationsImages.favicon(ENREGISTREE, 180)).contains("/c_fill,w_180,h_180,f_png,q_auto/v1732520000/")
                .doesNotContain("f_auto");
    }

    @Test
    void lImageDePartageEstRecadreeEn1200Par630() {
        String jpeg = "https://res.cloudinary.com/nuage/image/upload/f_auto,q_auto/v12/boutique/site/image-partage-abcdefgh.webp";

        assertThat(TransformationsImages.partage(jpeg)).isEqualTo(
                "https://res.cloudinary.com/nuage/image/upload/c_fill,g_auto,w_1200,h_630,f_jpg,q_auto/v12/boutique/site/image-partage-abcdefgh.jpg");
    }

    @Test
    void uneUrlQuiNEstPasEnregistreeEstRenvoyeeTelleQuelle() {
        assertThat(TransformationsImages.favicon("/images/demo/favicon.svg", 32)).isEqualTo("/images/demo/favicon.svg");
        assertThat(TransformationsImages.partage("https://exemple.sn/partage.jpg")).isEqualTo("https://exemple.sn/partage.jpg");
        String dejaDeclinee = TransformationsImages.favicon(ENREGISTREE, 32);
        assertThat(TransformationsImages.favicon(dejaDeclinee, 180)).as("jamais deux transformations").isEqualTo(dejaDeclinee);
        assertThat(TransformationsImages.partage(null)).isNull();
    }
}
