package sn.boutique.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

/** idPublic générés et création du client Cloudinary (sans appel réseau). */
class IdentifiantsEtStockageTest {

    private final IdentifiantsMedias identifiants =
            new IdentifiantsMedias(VerificationFichiersTest.proprietes(DataSize.ofMegabytes(5)));

    @Test
    void lIdPublicVientDuSlugAvecUnSuffixeAleatoire() {
        String idPublic = identifiants.nouvelIdPublic(UsageImage.PRODUIT, "asus-vivobook-15-x1504va");

        assertThat(idPublic).matches("dev/boutique/produits/asus-vivobook-15-x1504va-[a-z2-9]{8}");
        Set<String> tires = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            tires.add(identifiants.nouvelIdPublic(UsageImage.PRODUIT, "asus-vivobook-15-x1504va"));
        }
        assertThat(tires).as("aucune collision sur 1 000 tirages").hasSize(1_000);
        assertThat(identifiants.nouvelIdPublic(UsageImage.LOGO, "logo")).startsWith("dev/boutique/site/logo-");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Photo Awa Diop (1).JPG|photo-awa-diop-1-jpg",
            "../../etc/passwd|etc-passwd",
            "écran 27\" <script>|cran-27-script",
            "'   '|media",
            "---|media"})
    void toutNomEstReduitAuxCaracteresSurs(String nom, String attendu) {
        assertThat(IdentifiantsMedias.nettoyer(nom)).isEqualTo(attendu);
    }

    @Test
    void unNomTropLongEstTronque() {
        assertThat(IdentifiantsMedias.nettoyer("a-".repeat(100))).hasSizeLessThanOrEqualTo(80).doesNotEndWith("-");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "cloudinary://<your_api_key>:<your_api_secret>@dxyz",
            "https://cle:secret@exemple", "cloudinary://cle@nuage", "cloudinary://cle:secret@"})
    void uneCleAbsenteOuInvalideEstRefuseeSansEtreRecopiee(String url) {
        assertThatThrownBy(() -> StockageCloudinary.creerClient(url))
                .isInstanceOf(StockageIndisponibleException.class)
                .hasMessageContaining("CLOUDINARY_URL")
                .hasMessageNotContaining("your_api_secret")
                .hasMessageNotContaining("secret@");
    }

    @Test
    void uneCleBienFormeeDonneUnClientSecuriseSansAppelReseau() {
        Cloudinary client = StockageCloudinary.creerClient("cloudinary://123456789012345:abcdefSECRET@nuage-test");

        assertThat(client.config.cloudName).isEqualTo("nuage-test");
        assertThat(client.config.secure).isTrue();
    }

    @Test
    void sansCleUnEnvoiEchoueAvecUnMessageClair() {
        StockageCloudinary stockage = new StockageCloudinary(VerificationFichiersTest.proprietes(DataSize.ofMegabytes(5)));
        FichierVerifie fichier = new FichierVerifie(new byte[] {1}, "image/png", "png", 900, 900);

        assertThatThrownBy(() -> stockage.envoyerImage(fichier, "dev/boutique/produits/x-abcdefgh"))
                .isInstanceOf(StockageIndisponibleException.class)
                .hasMessageContaining("CLOUDINARY_URL");
    }
}
