package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeMedia;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.media.ImagesDeTest;
import sn.boutique.media.MediaOrphelin;
import sn.boutique.media.MediaOrphelinDepot;
import sn.boutique.support.FauxStockageMedia;

/**
 * Médias des produits dans le back-office : envoi (faux stockage, jamais Cloudinary), ordre, image
 * principale, textes alternatifs, suppression après validation, fichiers orphelins, limite, rôles.
 */
class AdminMediaTest extends CatalogueTestBase {

    private static final byte[] PHOTO = ImagesDeTest.image("png", 1000, 1000);

    @Autowired
    FauxStockageMedia stockage;
    @Autowired
    MediaOrphelinDepot orphelinDepot;
    @Autowired
    JdbcClient jdbc;

    private Categorie categorie;
    private Marque marque;

    @BeforeEach
    void preparer() {
        stockage.reinitialiser();
        categorie = categorie("PC portables médias", null, 0, true);
        marque = marque("Asus");
    }

    // --- Images de démonstration (servies par le site, sans idPublic) -----------------------------------

    @Test
    void lesImagesDeDemoSOrdonnentDeviennentPrincipalesEtSeSuppriment_sansAppelAuStockage() throws Exception {
        Produit produit = produitDemo(3);
        List<Long> ids = idsMedias(produit);
        // Le moindre appel au stockage échouerait
        stockage.provoquerEchecs(1_000);

        envoyer(put(medias(produit) + "/ordre").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\": [%d, %d, %d]}".formatted(ids.get(2), ids.get(0), ids.get(1))), "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medias[0].id").value(ids.get(2)))
                .andExpect(jsonPath("$.medias[0].stocke").value(false));

        envoyer(put(medias(produit) + "/" + ids.get(1) + "/principal"), "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medias[?(@.principal == true)].id").value(ids.get(1).intValue()));

        envoyer(delete(medias(produit) + "/" + ids.get(1)), "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medias.length()").value(2))
                .andExpect(jsonPath("$.medias[0].id").value(ids.get(2)))
                .andExpect(jsonPath("$.medias[0].principal").value(true))
                .andExpect(jsonPath("$.medias[0].position").value(0))
                .andExpect(jsonPath("$.medias[1].position").value(1));

        assertThat(stockage.suppressions()).noneMatch(id -> id == null || id.contains(produit.getSlug()));
        assertThat(orphelinDepot.findAll()).noneMatch(orphelin -> orphelin.getOrigine().contains(produit.getSlug()));
        // La fiche publique suit (cache invalidé)
        JsonNode fiche = corps(lire("/api/produits/" + produit.getSlug()));
        assertThat(fiche.get("medias").get(0).get("id").asLong()).isEqualTo(ids.get(2));
        assertThat(fiche.get("medias").get(0).get("principal").asBoolean()).isTrue();
    }

    // --- Envoi ---------------------------------------------------------------------------------------

    @Test
    void lEnvoiStockeLesImagesSousUnIdPublicIssuDuSlugEtUneUrlTransformee() throws Exception {
        Produit produit = produitDemo(0);
        Long id16 = variante(produit, "16 Go").getId();

        JsonNode reponse = corps(envoyer(envoi(produit, "Vivobook 15, vue de face", "Vivobook 15, clavier")
                .file(fichier("IMG_20240101_photo-awa-diop.png")).file(fichier("DSC0001.png"))
                .param("varianteId", id16.toString()), "GESTIONNAIRE_STOCK")
                .andExpect(status().isCreated()));

        JsonNode medias = reponse.get("medias");
        assertThat(medias).hasSize(2);
        assertThat(medias.get(0).get("texteAlternatif").asString()).as("pas de découpage sur la virgule")
                .isEqualTo("Vivobook 15, vue de face");
        assertThat(medias.get(0).get("principal").asBoolean()).as("première image d'un produit sans image").isTrue();
        assertThat(medias.get(1).get("principal").asBoolean()).isFalse();
        assertThat(medias.get(1).get("varianteId").asLong()).isEqualTo(id16);
        assertThat(medias.get(1).get("texteAlternatifSuggere").asString()).isEqualTo(produit.getNom() + " – 16 Go");
        assertThat(reponse.get("texteAlternatifSuggere").asString()).isEqualTo(produit.getNom());
        assertThat(reponse.get("suggestionsParVariante")).extracting(s -> s.get("texteAlternatifSuggere").asString())
                .contains(produit.getNom() + " – 8 Go", produit.getNom() + " – 16 Go");
        assertThat(reponse.get("maxImages").asInt()).isEqualTo(15);

        List<String> idsPublics = jdbc.sql("select id_public from media_produit where produit_id = ? order by position")
                .param(produit.getId()).query(String.class).list();
        for (int i = 0; i < 2; i++) {
            String idPublic = idsPublics.get(i);
            assertThat(idPublic).matches("dev/boutique/produits/" + produit.getSlug() + "-[a-z2-9]{8}")
                    .doesNotContainIgnoringCase("awa").doesNotContainIgnoringCase("dsc");
            assertThat(stockage.contient(idPublic)).isTrue();
            assertThat(medias.get(i).get("url").asString()).contains("/f_auto,q_auto/v").endsWith(idPublic + ".png");
            assertThat(medias.get(i).get("stocke").asBoolean()).isTrue();
        }
    }

    @Test
    void unFichierRefuseEmpecheToutEnvoi() throws Exception {
        Produit produit = produitDemo(1);

        envoyer(envoi(produit, "Vivobook 15 de face", "Vivobook 15 de dos").file(fichier("a.png"))
                .file(new MockMultipartFile("fichiers", "b.png", "image/png", ImagesDeTest.image("png", 1200, 700))),
                "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_TROP_PETITE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Fichier n° 2 :")));

        assertThat(stockage.suppressions()).isEmpty();
        assertThat(nombreMedias(produit)).isEqualTo(1);
    }

    @Test
    void unEchecDuStockageEnCoursDEnvoiSupprimeLesFichiersDejaEnvoyes() throws Exception {
        Produit produit = produitDemo(1);
        // 1er envoi réussi, 2e en échec
        stockage.provoquerEchecsApres(1, 1);

        envoyer(envoi(produit, "Vivobook 15 de face", "Vivobook 15 de dos").file(fichier("a.png")).file(fichier("b.png")),
                "ADMIN")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STOCKAGE_INDISPONIBLE"));

        assertThat(stockage.suppressions()).singleElement().asString().contains(produit.getSlug());
        assertThat(stockage.contient(stockage.suppressions().getFirst())).isFalse();
        assertThat(nombreMedias(produit)).isEqualTo(1);
    }

    @Test
    void auDelaDe15ImagesLEnvoiEstRefuseEn422SansRienEnvoyer() throws Exception {
        Produit produit = produitDemo(14);

        envoyer(envoi(produit, "Vivobook 15 de face", "Vivobook 15 de dos").file(fichier("a.png")).file(fichier("b.png")),
                "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TROP_D_IMAGES"));
        assertThat(stockage.suppressions()).isEmpty();
        assertThat(nombreMedias(produit)).isEqualTo(14);

        envoyer(envoi(produit, "Vivobook 15 de face").file(fichier("a.png")), "ADMIN").andExpect(status().isCreated());
        envoyer(envoi(produit, "Vivobook 15 de dos").file(fichier("b.png")), "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TROP_D_IMAGES"));
        assertThat(nombreMedias(produit)).isEqualTo(15);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IMG_1234.jpg", "DSC0001", "photo.png", "PXL_20240101_143022", "abc", "     "})
    void unTexteAlternatifQuiRessembleAUnNomDeFichierEstRefuse(String texte) throws Exception {
        Produit produit = produitDemo(1);

        envoyer(envoi(produit, texte).file(fichier("a.png")), "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"))
                .andExpect(jsonPath("$.parametre").value("textesAlternatifs[0]"));
        assertThat(stockage.suppressions()).isEmpty();
        assertThat(nombreMedias(produit)).isEqualTo(1);
    }

    @Test
    void ilFautUnTexteAlternatifParFichier() throws Exception {
        Produit produit = produitDemo(1);

        envoyer(envoi(produit, "Vivobook 15 de face").file(fichier("a.png")).file(fichier("b.png")), "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parametre").value("textesAlternatifs"));
        envoyer(envoi(produit).file(fichier("a.png")), "ADMIN").andExpect(status().isBadRequest());
        envoyer(envoi(produit, "Vivobook 15 de face"), "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parametre").value("fichiers"));
    }

    @Test
    void uneVarianteDUnAutreProduitEstRefusee() throws Exception {
        Produit produit = produitDemo(1);
        Produit autre = produitDemo(1);

        envoyer(envoi(produit, "Vivobook 15 de face").file(fichier("a.png"))
                .param("varianteId", variante(autre, "8 Go").getId().toString()), "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VARIANTE_AUTRE_PRODUIT"));
    }

    // --- Ordre, principale, modification ------------------------------------------------------------

    @Test
    void lOrdreDoitCiterChaqueMediaUneSeuleFois() throws Exception {
        Produit produit = produitDemo(3);
        List<Long> ids = idsMedias(produit);

        for (String liste : List.of("[%d, %d]".formatted(ids.get(0), ids.get(1)),
                "[%d, %d, %d]".formatted(ids.get(0), ids.get(0), ids.get(1)),
                "[%d, %d, %d, 999999]".formatted(ids.get(0), ids.get(1), ids.get(2)))) {
            envoyer(put(medias(produit) + "/ordre").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ids\": " + liste + "}"), "ADMIN")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("ORDRE_INCOMPLET"));
        }
        envoyer(put(medias(produit) + "/ordre").contentType(MediaType.APPLICATION_JSON).content("{\"ids\": []}"), "ADMIN")
                .andExpect(status().isBadRequest());
    }

    @Test
    void uneVideoNePeutPasEtreLImagePrincipale() throws Exception {
        Produit produit = produit("Vivobook vidéo " + unique("p"), marque, categorie, p -> {
            p.ajouterVariante(unique("SKU"), "8 Go", 389_000, 3);
            p.ajouterMedia(TypeMedia.IMAGE, "/images/demo/pc.svg", "Vivobook de face");
            p.ajouterMedia(TypeMedia.VIDEO_YOUTUBE, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Présentation vidéo");
        });

        envoyer(put(medias(produit) + "/" + idsMedias(produit).get(1) + "/principal"), "ADMIN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRINCIPAL_IMAGE_SEULEMENT"));
    }

    @Test
    void laModificationChangeLeTexteEtLaVariante() throws Exception {
        Produit produit = produitDemo(1);
        Long mediaId = idsMedias(produit).getFirst();
        Long id8 = variante(produit, "8 Go").getId();

        envoyer(patch(medias(produit) + "/" + mediaId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texteAlternatif\": \"  Vivobook 15, vue de trois quarts  \", \"varianteId\": " + id8 + "}"), "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medias[0].texteAlternatif").value("Vivobook 15, vue de trois quarts"))
                .andExpect(jsonPath("$.medias[0].varianteId").value(id8.intValue()))
                .andExpect(jsonPath("$.medias[0].libelleVariante").value("8 Go"));

        envoyer(patch(medias(produit) + "/" + mediaId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"varianteId\": null}"), "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medias[0].varianteId").doesNotExist())
                .andExpect(jsonPath("$.medias[0].texteAlternatif").value("Vivobook 15, vue de trois quarts"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"texteAlternatif\": null}", "{\"texteAlternatif\": \"IMG_1234.jpg\"}",
            "{\"texteAlternatif\": \"abc\"}"})
    void uneModificationInvalideRenvoie400(String json) throws Exception {
        Produit produit = produitDemo(1);

        envoyer(patch(medias(produit) + "/" + idsMedias(produit).getFirst()).contentType(MediaType.APPLICATION_JSON)
                .content(json), "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void unMediaDUnAutreProduitEstIntrouvable() throws Exception {
        Produit produit = produitDemo(1);
        Produit autre = produitDemo(1);

        envoyer(delete(medias(produit) + "/" + idsMedias(autre).getFirst()), "ADMIN").andExpect(status().isNotFound());
        envoyer(get("/api/admin/produits/999999999/medias"), "ADMIN").andExpect(status().isNotFound());
    }

    // --- Suppression ----------------------------------------------------------------------------------

    @Test
    void laDerniereImageDUnProduitPublieNePeutPasEtreSupprimee() throws Exception {
        Produit produit = produitDemo(1);

        envoyer(delete(medias(produit) + "/" + idsMedias(produit).getFirst()), "ADMIN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DERNIERE_IMAGE"));

        enTransaction(em -> {
            em.find(Produit.class, produit.getId()).setActif(false);
            return null;
        });
        envoyer(delete(medias(produit) + "/" + idsMedias(produit).getFirst()), "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medias.length()").value(0));
    }

    @Test
    void laSuppressionRetireLeFichierDuStockageApresLaValidation() throws Exception {
        Produit produit = produitDemo(1);
        JsonNode envoye = corps(envoyer(envoi(produit, "Vivobook 15 de dos").file(fichier("a.png")), "ADMIN"));
        Long mediaId = envoye.get("medias").get(1).get("id").asLong();
        String idPublic = idPublic(mediaId);

        envoyer(delete(medias(produit) + "/" + mediaId), "ADMIN").andExpect(status().isOk());

        attendre("suppression sur le stockage", () -> stockage.suppressions().contains(idPublic));
        assertThat(stockage.contient(idPublic)).isFalse();
        assertThat(orphelinDepot.findByIdPublic(idPublic)).isEmpty();
    }

    @Test
    void apresTroisEchecsLeFichierEstEnregistreCommeOrphelin() throws Exception {
        Produit produit = produitDemo(1);
        JsonNode envoye = corps(envoyer(envoi(produit, "Vivobook 15 de dos").file(fichier("a.png")), "ADMIN"));
        Long mediaId = envoye.get("medias").get(1).get("id").asLong();
        String idPublic = idPublic(mediaId);
        stockage.provoquerEchecs(3);

        envoyer(delete(medias(produit) + "/" + mediaId), "ADMIN").andExpect(status().isOk());

        attendre("enregistrement de l'orphelin", () -> orphelinDepot.findByIdPublic(idPublic).isPresent());
        MediaOrphelin orphelin = orphelinDepot.findByIdPublic(idPublic).orElseThrow();
        assertThat(orphelin.getTentatives()).isEqualTo(3);
        assertThat(orphelin.getTypeRessource()).hasToString("IMAGE");
        assertThat(orphelin.getOrigine()).contains("média " + mediaId).contains(produit.getSlug());
        assertThat(orphelin.getDerniereErreur()).contains("Échec simulé");
        assertThat(nombreMedias(produit)).as("la suppression en base reste acquise").isEqualTo(1);
        assertThat(stockage.contient(idPublic)).isTrue();
    }

    @Test
    void sansEchecDefinitifAucunOrphelinNEstEnregistre() throws Exception {
        Produit produit = produitDemo(1);
        JsonNode envoye = corps(envoyer(envoi(produit, "Vivobook 15 de dos").file(fichier("a.png")), "ADMIN"));
        Long mediaId = envoye.get("medias").get(1).get("id").asLong();
        String idPublic = idPublic(mediaId);
        stockage.provoquerEchecs(2);

        envoyer(delete(medias(produit) + "/" + mediaId), "ADMIN").andExpect(status().isOk());

        attendre("suppression au 3e essai", () -> stockage.suppressions().contains(idPublic));
        assertThat(orphelinDepot.findByIdPublic(idPublic)).isEmpty();
    }

    // --- Variantes supprimées : les médias restent rattachés au produit ----------------------------------

    @Test
    void laCleEtrangereVersLaVarianteEstEnSetNull() {
        String regle = jdbc.sql("""
                        select confdeltype from pg_constraint
                        where conrelid = 'media_produit'::regclass and conname = 'fk_media_produit_variante'""")
                .query(String.class).single();
        assertThat(regle).as("n = ON DELETE SET NULL").isEqualTo("n");
    }

    @Test
    void supprimerUneVarianteConserveSesMediasSurLeProduit() {
        Produit produit = produit("Vivobook couleurs " + unique("p"), marque, categorie, p -> {
            p.ajouterVariante(unique("SKU"), "Gris", 389_000, 3);
            p.ajouterVariante(unique("SKU"), "Bleu", 399_000, 3);
            p.ajouterMedia(TypeMedia.IMAGE, "/images/demo/gris.svg", "Vivobook gris de face");
            p.ajouterMedia(TypeMedia.IMAGE, "/images/demo/bleu.svg", "Vivobook bleu de face");
        });
        Long idBleu = variante(produit, "Bleu").getId();
        Long mediaBleu = idsMedias(produit).get(1);
        jdbc.sql("update media_produit set variante_id = ? where id = ?").param(idBleu).param(mediaBleu).update();

        // Suppression par JPA (comme le fera le back-office), puis vérification en base
        enTransaction(em -> {
            Produit charge = em.find(Produit.class, produit.getId());
            charge.getVariantes().removeIf(variante -> variante.getId().equals(idBleu));
            return null;
        });

        assertThat(jdbc.sql("select count(*) from variante_produit where id = ?").param(idBleu).query(Long.class).single())
                .isZero();
        assertThat(jdbc.sql("select variante_id from media_produit where id = ?").param(mediaBleu)
                .query((ligne, n) -> Optional.ofNullable(ligne.getObject(1))).single())
                .as("média conservé, variante remise à null").isEmpty();
        assertThat(nombreMedias(produit)).isEqualTo(2);
    }

    // --- Rôles ---------------------------------------------------------------------------------------

    @Test
    void lesRolesAutorises() throws Exception {
        Produit produit = produitDemo(1);

        mockMvc.perform(get(medias(produit))).andExpect(status().isUnauthorized());
        envoyer(get(medias(produit)), "CLIENT").andExpect(status().isForbidden());
        envoyer(get(medias(produit)), "SERVICE_CLIENT").andExpect(status().isForbidden());
        for (String role : List.of("GESTIONNAIRE_STOCK", "ADMIN", "SUPER_ADMIN")) {
            envoyer(get(medias(produit)), role).andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombreImages").value(1));
        }
    }

    // ------------------------------------------------------------------------------------------------

    /** Produit actif à deux variantes (8 Go, 16 Go) et {@code images} images de démonstration (sans idPublic). */
    private Produit produitDemo(int images) {
        return produit("Vivobook 15 " + unique("m"), marque, categorie, p -> {
            p.ajouterVariante(unique("SKU-8"), "8 Go", 389_000, 3);
            p.ajouterVariante(unique("SKU-16"), "16 Go", 449_000, 2);
            for (int i = 0; i < images; i++) {
                p.ajouterMedia(TypeMedia.IMAGE, "/images/demo/pc-" + i + ".svg", "Vivobook 15, vue " + (i + 1));
            }
        });
    }

    private static String medias(Produit produit) {
        return "/api/admin/produits/" + produit.getId() + "/medias";
    }

    private MockMultipartHttpServletRequestBuilder envoi(Produit produit, String... textes) {
        MockMultipartHttpServletRequestBuilder requete = multipart(medias(produit));
        for (String texte : textes) {
            requete.param("textesAlternatifs", texte);
        }
        return requete;
    }

    private static MockMultipartFile fichier(String nomOrigine) {
        return new MockMultipartFile("fichiers", nomOrigine, "image/png", PHOTO);
    }

    private ResultActions envoyer(AbstractMockHttpServletRequestBuilder<?> requete, String role) throws Exception {
        return mockMvc.perform(requete.with(sn.boutique.support.JwtEquipe.pour(jdbc, role)));
    }

    private List<Long> idsMedias(Produit produit) {
        return jdbc.sql("select id from media_produit where produit_id = ? order by position")
                .param(produit.getId()).query(Long.class).list();
    }

    private long nombreMedias(Produit produit) {
        return idsMedias(produit).size();
    }

    private String idPublic(Long mediaId) {
        return jdbc.sql("select id_public from media_produit where id = ?").param(mediaId).query(String.class).single();
    }

    private VarianteProduit variante(Produit produit, String libelle) {
        return enTransaction(em -> em.find(Produit.class, produit.getId()).getVariantes().stream()
                .filter(variante -> variante.getLibelle().equals(libelle)).findFirst().orElseThrow());
    }

    /** La suppression sur le stockage se fait en tâche de fond, après la validation. */
    private static void attendre(String description, BooleanSupplier condition) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        List<Boolean> essais = new ArrayList<>();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                fail("Délai dépassé en attendant : " + description + " (" + essais.size() + " vérifications)");
            }
            essais.add(false);
            Thread.sleep(25);
        }
    }
}
