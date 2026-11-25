package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import sn.boutique.media.ImagesDeTest;
import sn.boutique.support.FauxStockageMedia;
import tools.jackson.databind.JsonNode;

/** Images des paramètres du site (logo, favicon, partage) et des bannières : règles, URL déclinées, cache. */
class AdminImagesSiteTest extends CatalogueTestBase {

    @Autowired
    FauxStockageMedia stockage;
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    @AfterEach
    void viderLesImagesDesParametres() {
        jdbc.sql("""
                update parametres_site set logo_url = null, logo_id_public = null, logo_sombre_url = null,
                    logo_sombre_id_public = null, favicon_url = null, favicon_id_public = null,
                    image_partage_url = null, image_partage_id_public = null""").update();
        viderLesCaches();
        stockage.reinitialiser();
    }

    // --- Cache des paramètres ------------------------------------------------------------------------

    @Test
    void remplacerLeLogoVideLeCacheDesParametres() throws Exception {
        assertThat(parametres().get("logoUrl").isNull()).isTrue();
        // Preuve que la réponse est en cache : une écriture directe en base n'est pas vue
        jdbc.sql("update parametres_site set logo_url = '/images/demo/logo.svg'").update();
        assertThat(parametres().get("logoUrl").isNull()).as("réponse servie par le cache").isTrue();

        envoyer("logo", png(1200, 400)).andExpect(status().isOk());

        String logo = parametres().get("logoUrl").asString();
        assertThat(logo).startsWith("https://res.cloudinary.test/").contains("/f_auto,q_auto/v")
                .contains("/dev/boutique/site/logo-");
    }

    @Test
    void remplacerLeFaviconVideLeCacheEtSupprimeLAncienFichier() throws Exception {
        assertThat(parametres().get("favicons").isNull()).isTrue();

        envoyer("favicon", png(512, 512)).andExpect(status().isOk());
        JsonNode favicons = parametres().get("favicons");
        assertThat(favicons.get("taille32").asString()).contains("/c_fill,w_32,h_32,f_png,q_auto/v").endsWith(".png");
        assertThat(favicons.get("appleTouch180").asString()).contains("/c_fill,w_180,h_180,f_png,q_auto/");
        assertThat(favicons.get("manifeste192").asString()).contains("/c_fill,w_192,h_192,f_png,q_auto/");
        assertThat(favicons.get("manifeste512").asString()).contains("/c_fill,w_512,h_512,f_png,q_auto/");
        String premier = idPublic("favicon");

        envoyer("favicon", png(1024, 1024)).andExpect(status().isOk());
        assertThat(idPublic("favicon")).isNotEqualTo(premier);
        assertThat(parametres().get("favicons").get("taille32").asString()).contains(idPublic("favicon"));
        attendre(() -> stockage.suppressions().contains(premier));
        assertThat(stockage.contient(premier)).isFalse();
    }

    @Test
    void leFaviconDoitEtreCarreEtAssezGrand() throws Exception {
        envoyer("favicon", png(600, 512)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_NON_CARREE"));
        envoyer("favicon", png(256, 256)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_TROP_PETITE"));
        envoyer("favicon", svg()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TYPE_NON_AUTORISE"));
        assertThat(stockage.suppressions()).isEmpty();
    }

    @Test
    void lImageDePartageEstRefuseeSous600Par315EtRecadreeEn1200Par630() throws Exception {
        envoyer("image-partage", png(590, 400)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_TROP_PETITE"));
        envoyer("image-partage", png(800, 300)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("600 × 315")));

        envoyer("image-partage", png(600, 315)).andExpect(status().isOk())
                .andExpect(jsonPath("$.imagePartageUrl").value(org.hamcrest.Matchers.containsString(
                        "/c_fill,g_auto,w_1200,h_630,f_jpg,q_auto/v")));
    }

    @Test
    void leLogoPeutEtreUnSvgNettoye() throws Exception {
        envoyer("logo-sombre", svg()).andExpect(status().isOk())
                .andExpect(jsonPath("$.logoSombreUrl").value(org.hamcrest.Matchers.endsWith(".svg")))
                // Jamais f_auto sur un SVG : il serait converti en image matricielle
                .andExpect(jsonPath("$.logoSombreUrl").value(org.hamcrest.Matchers.containsString("/image/upload/fl_sanitize/v")));

        String contenu = new String(stockage.fichier(idPublic("logo_sombre")).contenu(), StandardCharsets.UTF_8);
        assertThat(contenu).doesNotContain("script").contains("<rect");
    }

    @Test
    void imageInconnueEtRoles() throws Exception {
        envoyer("arriere-plan", png(512, 512)).andExpect(status().isNotFound());
        envoyer("logo", png(512, 512), "GESTIONNAIRE_STOCK").andExpect(status().isForbidden());
        envoyer("logo", png(512, 512), "SERVICE_CLIENT").andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/parametres/logo").file(fichier(png(512, 512))))
                .andExpect(status().isUnauthorized());
    }

    // --- Bannières -----------------------------------------------------------------------------------

    @Test
    void uneImageDeBanniereAuFormatConseilleNaPasDAvertissement() throws Exception {
        banniere(png(1920, 600), "ORDINATEUR", "ADMIN")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPublic").value(org.hamcrest.Matchers.startsWith("dev/boutique/bannieres/banniere-ordinateur-")))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("/f_auto,q_auto/v")))
                .andExpect(jsonPath("$.formatConseille").value("1920 × 600 px"))
                .andExpect(jsonPath("$.avertissements.length()").value(0));
        banniere(png(800, 1000), "MOBILE", "ADMIN")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avertissements.length()").value(0));
    }

    @Test
    void desProportionsEloigneesNeBloquentPasMaisAvertissent() throws Exception {
        banniere(png(1000, 1000), "ORDINATEUR", "ADMIN")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avertissements.length()").value(2))
                .andExpect(jsonPath("$.avertissements[0]").value(org.hamcrest.Matchers.containsString("recadrée")));
        banniere(png(1920, 600), "MOBILE", "ADMIN")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avertissements[0]").value(org.hamcrest.Matchers.containsString("800 × 1000")));
    }

    @Test
    void lesBannieresRestentControleesEtReserveesALAdministration() throws Exception {
        banniere(png(1920, 500), "ORDINATEUR", "ADMIN").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_TROP_PETITE"));
        banniere(svg(), "ORDINATEUR", "ADMIN").andExpect(status().isUnprocessableContent());
        mockMvc.perform(multipart("/api/admin/bannieres/images").file(fichier(png(1920, 600)))
                        .with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
        banniere(png(1920, 600), "ORDINATEUR", "GESTIONNAIRE_STOCK").andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------------------

    private JsonNode parametres() throws Exception {
        return corps(lire("/api/parametres").andExpect(status().isOk()));
    }

    private ResultActions envoyer(String image, byte[] contenu) throws Exception {
        return envoyer(image, contenu, "ADMIN");
    }

    private ResultActions envoyer(String image, byte[] contenu, String role) throws Exception {
        return mockMvc.perform(avecRole(multipart("/api/admin/parametres/" + image).file(fichier(contenu)), role));
    }

    private ResultActions banniere(byte[] contenu, String format, String role) throws Exception {
        return mockMvc.perform(avecRole(multipart("/api/admin/bannieres/images").file(fichier(contenu))
                .param("format", format), role));
    }

    private static MockMultipartHttpServletRequestBuilder avecRole(MockMultipartHttpServletRequestBuilder requete,
                                                                   String role) {
        requete.with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_" + role)));
        return requete;
    }

    private static MockMultipartFile fichier(byte[] contenu) {
        return new MockMultipartFile("fichier", "mon-logo.png", "image/png", contenu);
    }

    private static byte[] png(int largeur, int hauteur) {
        return ImagesDeTest.image("png", largeur, hauteur);
    }

    private static byte[] svg() {
        return ("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><script>alert(1)</script>"
                + "<rect width=\"10\" height=\"10\" fill=\"#C2410C\"/></svg>").getBytes(StandardCharsets.UTF_8);
    }

    private String idPublic(String colonne) {
        return jdbc.sql("select " + colonne + "_id_public from parametres_site").query(String.class).single();
    }

    private static void attendre(BooleanSupplier condition) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                fail("Délai dépassé en attendant la suppression de l'ancien fichier");
            }
            Thread.sleep(25);
        }
    }
}
