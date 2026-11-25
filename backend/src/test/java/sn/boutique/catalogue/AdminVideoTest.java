package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeMedia;
import sn.boutique.media.YoutubeIndisponibleException;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.support.FauxStockageMedia;
import sn.boutique.support.FauxVerificationYoutube;
import tools.jackson.databind.JsonNode;

/**
 * Vidéos des produits : signature d'envoi propre au produit, déclaration vérifiée, traces des signatures,
 * liens YouTube vérifiés (faux oEmbed), miniatures en URL transformée.
 */
class AdminVideoTest extends CatalogueTestBase {

    private static final String ID_YOUTUBE = "dQw4w9WgXcQ";

    @Autowired
    FauxStockageMedia stockage;
    @Autowired
    FauxVerificationYoutube youtube;
    @Autowired
    JdbcClient jdbc;

    private Categorie categorie;
    private Marque marque;

    @BeforeEach
    void preparer() {
        stockage.reinitialiser();
        youtube.reinitialiser();
        categorie = categorie("PC portables vidéos", null, 0, true);
        marque = marque("Asus");
    }

    // --- Signature ----------------------------------------------------------------------------------

    @Test
    void laSignatureEstPropreAuDossierDuProduitCourteEtEnregistree() throws Exception {
        Produit produit = produitAvecImage();
        Instant maintenant = horloge.instant();

        JsonNode signature = signer(produit);

        String idPublic = signature.get("idPublic").asString();
        assertThat(idPublic).matches("dev/boutique/produits/" + produit.getId() + "/videos/" + produit.getSlug()
                + "-[a-z2-9]{8}");
        assertThat(Instant.parse(signature.get("expiration").asString())).isEqualTo(maintenant.plus(Duration.ofMinutes(50)));
        assertThat(signature.get("tailleMax").asLong()).isEqualTo(100L * 1024 * 1024);
        assertThat(signature.get("formats")).extracting(JsonNode::asString).containsExactly("mp4", "webm");
        assertThat(signature.get("parametres").get("public_id").asString()).isEqualTo(idPublic);
        // Antidatée : Cloudinary accepte 1 h, il reste donc 50 min
        assertThat(stockage.horodatageSigne(idPublic)).isEqualTo(maintenant.minus(Duration.ofMinutes(10)));

        var trace = jdbc.sql("select produit_id, dossier, date_expiration, date_utilisation from signature_envoi_video "
                        + "where id_public = ?").param(idPublic).query().singleRow();
        assertThat(trace.get("produit_id")).isEqualTo(produit.getId());
        assertThat(trace.get("dossier")).isEqualTo("dev/boutique/produits/" + produit.getId() + "/videos");
        assertThat(trace.get("date_utilisation")).isNull();
    }

    // --- Déclaration --------------------------------------------------------------------------------

    @Test
    void uneVideoEnvoyeeEtDeclareeDevientUnMediaAvecUneMiniatureTransformee() throws Exception {
        Produit produit = produitAvecImage();
        String idPublic = signer(produit).get("idPublic").asString();
        stockage.simulerEnvoiVideo(idPublic, "mp4", 50_000_000L);

        JsonNode reponse = corps(declarer(produit, idPublic, "Démonstration du Vivobook 15", "ADMIN")
                .andExpect(status().isCreated()));

        JsonNode video = reponse.get("medias").get(1);
        assertThat(video.get("type").asString()).isEqualTo("VIDEO_FICHIER");
        assertThat(video.get("url").asString()).contains("/video/upload/f_auto,q_auto/v").endsWith(idPublic + ".mp4");
        assertThat(video.get("urlMiniature").asString()).contains("/video/upload/so_auto,f_auto,q_auto/v")
                .endsWith(idPublic + ".jpg");
        assertThat(video.get("principal").asBoolean()).isFalse();
        assertThat(reponse.get("nombreVideos").asInt()).isEqualTo(1);
        assertThat(reponse.get("maxVideos").asInt()).isEqualTo(1);
        assertThat(jdbc.sql("select date_utilisation from signature_envoi_video where id_public = ?").param(idPublic)
                .query(Object.class).single()).isNotNull();
        // Fiche publique : la vidéo et son aperçu
        JsonNode fiche = corps(lire("/api/produits/" + produit.getSlug()));
        assertThat(fiche.get("medias").get(1).get("urlMiniature").asString()).contains("so_auto,f_auto,q_auto");
    }

    @Test
    void uneVideoSigneePourUnAutreProduitEstRefuseeSansEtreSupprimee() throws Exception {
        Produit produit = produitAvecImage();
        Produit autre = produitAvecImage();
        String idAutre = signer(autre).get("idPublic").asString();
        stockage.simulerEnvoiVideo(idAutre, "mp4", 1_000_000L);

        declarer(produit, idAutre, "Démonstration du Vivobook 15", "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VIDEO_AUTRE_PRODUIT"));
        assertThat(stockage.contient(idAutre)).as("la vidéo de l'autre produit reste intacte").isTrue();

        // idPublic forgé dans le dossier du produit, sans signature émise
        String forge = "dev/boutique/produits/" + produit.getId() + "/videos/" + produit.getSlug() + "-abcdefgh";
        stockage.simulerEnvoiVideo(forge, "mp4", 1_000_000L);
        declarer(produit, forge, "Démonstration du Vivobook 15", "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VIDEO_AUTRE_PRODUIT"));
        assertThat(nombreVideos(produit)).isZero();
    }

    @Test
    void uneVideoNeSeDeclareQuUneFois() throws Exception {
        Produit produit = produitAvecImage();
        String idPublic = signer(produit).get("idPublic").asString();
        stockage.simulerEnvoiVideo(idPublic, "webm", 1_000_000L);
        declarer(produit, idPublic, "Démonstration du Vivobook 15", "ADMIN").andExpect(status().isCreated());

        declarer(produit, idPublic, "Démonstration du Vivobook 15", "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VIDEO_DEJA_DECLAREE"));
        assertThat(stockage.contient(idPublic)).as("la vidéo déjà enregistrée n'est pas supprimée").isTrue();
        assertThat(nombreVideos(produit)).isEqualTo(1);
    }

    @Test
    void uneVideoAbsenteDuStockageEstSignalee() throws Exception {
        Produit produit = produitAvecImage();
        String idPublic = signer(produit).get("idPublic").asString();

        declarer(produit, idPublic, "Démonstration du Vivobook 15", "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VIDEO_INTROUVABLE"));
    }

    @Test
    void unFormatOuUnPoidsRefusesSupprimentLaVideo() throws Exception {
        Produit produit = produitAvecImage();
        String mov = signer(produit).get("idPublic").asString();
        stockage.simulerEnvoiVideo(mov, "mov", 1_000_000L);
        declarer(produit, mov, "Démonstration du Vivobook 15", "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VIDEO_INVALIDE"));
        assertThat(stockage.contient(mov)).isFalse();

        String lourde = signer(produit).get("idPublic").asString();
        stockage.simulerEnvoiVideo(lourde, "mp4", 100L * 1024 * 1024 + 1);
        declarer(produit, lourde, "Démonstration du Vivobook 15", "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VIDEO_INVALIDE"));
        assertThat(stockage.contient(lourde)).isFalse();
        assertThat(nombreVideos(produit)).isZero();
    }

    @Test
    void uneSignatureExpireeEstRefusee() throws Exception {
        Produit produit = produitAvecImage();
        String idPublic = signer(produit).get("idPublic").asString();
        stockage.simulerEnvoiVideo(idPublic, "mp4", 1_000_000L);

        // Envoi commencé avant l'expiration, déclaration tardive mais dans le délai : acceptée
        horloge.avancer(Duration.ofMinutes(50).plus(Duration.ofMinutes(59)));
        String autre = signer(produitAvecImage()).get("idPublic").asString();
        assertThat(autre).isNotEqualTo(idPublic);

        horloge.avancer(Duration.ofMinutes(2));
        declarer(produit, idPublic, "Démonstration du Vivobook 15", "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SIGNATURE_EXPIREE"));
    }

    @Test
    void lesVideosJamaisDeclareesSeRetrouventParLeursSignatures() throws Exception {
        Produit produit = produitAvecImage();
        String abandonnee = signer(produit).get("idPublic").asString();
        stockage.simulerEnvoiVideo(abandonnee, "mp4", 1_000_000L); // navigateur fermé avant la déclaration
        horloge.avancer(Duration.ofHours(2));

        List<String> aNettoyer = jdbc.sql("""
                        select id_public from signature_envoi_video
                        where date_utilisation is null and date_expiration < ? and produit_id = ?""")
                .param(java.sql.Timestamp.from(horloge.instant())).param(produit.getId()).query(String.class).list();

        assertThat(aNettoyer).containsExactly(abandonnee);
    }

    @Test
    void uneSeuleVideoParProduit() throws Exception {
        Produit produit = produitAvecImage();
        String idPublic = signer(produit).get("idPublic").asString();
        stockage.simulerEnvoiVideo(idPublic, "mp4", 1_000_000L);
        declarer(produit, idPublic, "Démonstration du Vivobook 15", "ADMIN").andExpect(status().isCreated());

        envoyer(post(medias(produit) + "/videos/signature"), "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TROP_DE_VIDEOS"));
        ajouterYoutube(produit, "https://youtu.be/" + ID_YOUTUBE, "Présentation du Vivobook 15")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TROP_DE_VIDEOS"));
    }

    @Test
    void supprimerUneVideoLaRetireDuStockage() throws Exception {
        Produit produit = produitAvecImage();
        String idPublic = signer(produit).get("idPublic").asString();
        stockage.simulerEnvoiVideo(idPublic, "mp4", 1_000_000L);
        Long mediaId = corps(declarer(produit, idPublic, "Démonstration du Vivobook 15", "ADMIN"))
                .get("medias").get(1).get("id").asLong();

        envoyer(delete(medias(produit) + "/" + mediaId), "ADMIN").andExpect(status().isOk());

        attendre(() -> stockage.suppressions().contains(idPublic));
        assertThat(stockage.contient(idPublic)).isFalse();
    }

    // --- YouTube -------------------------------------------------------------------------------------

    @Test
    void unLienYoutubeValideEstEnregistreAvecSaMiniatureCopieeSurLeStockage() throws Exception {
        Produit produit = produitAvecImage();

        JsonNode reponse = corps(ajouterYoutube(produit, "https://www.youtube.com/shorts/" + ID_YOUTUBE,
                "Présentation du Vivobook 15").andExpect(status().isCreated()));

        JsonNode video = reponse.get("medias").get(1);
        assertThat(video.get("type").asString()).isEqualTo("VIDEO_YOUTUBE");
        assertThat(video.get("url").asString()).isEqualTo("https://www.youtube.com/watch?v=" + ID_YOUTUBE);
        String idMiniature = jdbc.sql("select id_public from media_produit where id = ?")
                .param(video.get("id").asLong()).query(String.class).single();
        assertThat(idMiniature).startsWith("dev/boutique/produits/" + produit.getSlug() + "-youtube-");
        assertThat(stockage.urlDistante(idMiniature)).isEqualTo("https://i.ytimg.com/vi/" + ID_YOUTUBE + "/hqdefault.jpg");
        assertThat(video.get("urlMiniature").asString()).contains("/image/upload/f_auto,q_auto/v");
        JsonNode fiche = corps(lire("/api/produits/" + produit.getSlug()));
        assertThat(fiche.get("medias").get(1).get("idYoutube").asString()).isEqualTo(ID_YOUTUBE);
    }

    @Test
    void unLienQuiNEstPasYoutubeEstRefuse() throws Exception {
        Produit produit = produitAvecImage();

        ajouterYoutube(produit, "https://evil.example/watch?v=" + ID_YOUTUBE, "Présentation du Vivobook 15")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LIEN_YOUTUBE_INVALIDE"));
    }

    @Test
    void uneVideoPriveeOuNonIntegrableEstRefuseeAvecUnMessageClair() throws Exception {
        Produit produit = produitAvecImage();
        youtube.repondre(ID_YOUTUBE, new RegleMetierException("YOUTUBE_NON_INTEGRABLE",
                "Cette vidéo YouTube est privée ou son auteur a désactivé l'intégration sur d'autres sites."));

        ajouterYoutube(produit, "https://youtu.be/" + ID_YOUTUBE, "Présentation du Vivobook 15")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("YOUTUBE_NON_INTEGRABLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("privée")));
        assertThat(nombreVideos(produit)).isZero();
    }

    @Test
    void unYoutubeQuiNeRepondPasDonneUne502Explicite() throws Exception {
        Produit produit = produitAvecImage();
        youtube.repondre(ID_YOUTUBE, new YoutubeIndisponibleException("délai dépassé", null));

        ajouterYoutube(produit, "https://youtu.be/" + ID_YOUTUBE, "Présentation du Vivobook 15")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("YOUTUBE_INDISPONIBLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("YouTube ne répond pas")));
        assertThat(nombreVideos(produit)).isZero();
    }

    @Test
    void leTexteAlternatifDUneVideoEstObligatoireEtControle() throws Exception {
        Produit produit = produitAvecImage();

        ajouterYoutube(produit, "https://youtu.be/" + ID_YOUTUBE, "VID_20240101_101010.mp4")
                .andExpect(status().isBadRequest());
        envoyer(post(medias(produit) + "/youtube").contentType(MediaType.APPLICATION_JSON)
                .content("{\"lien\": \"https://youtu.be/" + ID_YOUTUBE + "\"}"), "ADMIN")
                .andExpect(status().isBadRequest());
    }

    @Test
    void leServiceClientNeGerePasLesVideos() throws Exception {
        Produit produit = produitAvecImage();

        envoyer(post(medias(produit) + "/videos/signature"), "SERVICE_CLIENT").andExpect(status().isForbidden());
        envoyer(post(medias(produit) + "/videos/signature"), "GESTIONNAIRE_STOCK").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------------------

    private Produit produitAvecImage() {
        return produit("Vivobook 15 " + unique("v"), marque, categorie, p -> {
            p.ajouterVariante(unique("SKU"), "8 Go", 389_000, 3);
            p.ajouterMedia(TypeMedia.IMAGE, "/images/demo/pc.svg", "Vivobook 15 de face");
        });
    }

    private JsonNode signer(Produit produit) throws Exception {
        return corps(envoyer(post(medias(produit) + "/videos/signature"), "ADMIN").andExpect(status().isOk()));
    }

    private ResultActions declarer(Produit produit, String idPublic, String texte, String role) throws Exception {
        return envoyer(post(medias(produit) + "/videos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPublic\": \"%s\", \"texteAlternatif\": \"%s\"}".formatted(idPublic, texte)), role);
    }

    private ResultActions ajouterYoutube(Produit produit, String lien, String texte) throws Exception {
        return envoyer(post(medias(produit) + "/youtube").contentType(MediaType.APPLICATION_JSON)
                .content("{\"lien\": \"%s\", \"texteAlternatif\": \"%s\"}".formatted(lien, texte)), "ADMIN");
    }

    private static String medias(Produit produit) {
        return "/api/admin/produits/" + produit.getId() + "/medias";
    }

    private ResultActions envoyer(AbstractMockHttpServletRequestBuilder<?> requete, String role) throws Exception {
        return mockMvc.perform(requete.with(jwt().jwt(j -> j.subject("1"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private long nombreVideos(Produit produit) {
        return jdbc.sql("select count(*) from media_produit where produit_id = ? and type <> 'IMAGE'")
                .param(produit.getId()).query(Long.class).single();
    }

    private static void attendre(BooleanSupplier condition) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                fail("Délai dépassé en attendant la suppression sur le stockage");
            }
            Thread.sleep(25);
        }
    }
}
