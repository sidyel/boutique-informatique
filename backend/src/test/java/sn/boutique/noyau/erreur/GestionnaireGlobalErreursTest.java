package sn.boutique.noyau.erreur;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import sn.boutique.authentification.securite.ReponsesSecurite;
import sn.boutique.noyau.config.HorlogeConfig;
import sn.boutique.noyau.config.ProprietesBoutique;
import sn.boutique.noyau.config.SecuriteConfig;

@WebMvcTest(controllers = GestionnaireGlobalErreursTest.ControleurEssai.class)
@Import({SecuriteConfig.class, ReponsesSecurite.class, ProblemesHttp.class, HorlogeConfig.class, GestionnaireGlobalErreursTest.ControleurEssai.class})
@EnableConfigurationProperties(ProprietesBoutique.class)
@TestPropertySource(properties = {
        "boutique.url-site=http://localhost:4200",
        "boutique.cors-origines=http://localhost:4200"
})
class GestionnaireGlobalErreursTest {

    @Autowired
    private MockMvc mockMvc;

    /** Exigé par la configuration de sécurité (jetons d'accès) ; inutilisé par ces routes publiques. */
    @MockitoBean
    private JwtDecoder decodeurJwt;

    @Test
    void ressourceIntrouvableRenvoie404() throws Exception {
        mockMvc.perform(get("/api/essai/produits/asus-inconnu"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Ressource introuvable"))
                .andExpect(jsonPath("$.detail").value("Produit introuvable : asus-inconnu"))
                .andExpect(jsonPath("$.code").value("RESSOURCE_INTROUVABLE"))
                .andExpect(jsonPath("$.instance").value("/api/essai/produits/asus-inconnu"))
                .andExpect(jsonPath("$.horodatage").exists());
    }

    @Test
    void stockInsuffisantRenvoie409AvecLeSkuEtLeStock() throws Exception {
        mockMvc.perform(get("/api/essai/stock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_INSUFFISANT"))
                .andExpect(jsonPath("$.sku").value("ASUS-FA507-16-512"))
                .andExpect(jsonPath("$.stockDisponible").value(1));
    }

    @Test
    void regleMetierRenvoie422AvecSonCode() throws Exception {
        mockMvc.perform(get("/api/essai/code-promo"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Règle métier non respectée"))
                .andExpect(jsonPath("$.code").value("CODE_PROMO_EXPIRE"))
                .andExpect(jsonPath("$.detail").value("Ce code promo a expiré."));
    }

    @Test
    void erreurInattendueRenvoie500SansDetailTechnique() throws Exception {
        mockMvc.perform(get("/api/essai/panne"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ERREUR_INTERNE"))
                .andExpect(jsonPath("$.detail")
                        .value("Une erreur inattendue est survenue. Veuillez réessayer plus tard."));
    }

    @Test
    void validationRenvoie400AvecLaListeDesChampsEnErreur() throws Exception {
        mockMvc.perform(post("/api/essai/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "pas-un-email", "prenom": "", "age": 12}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Données invalides"))
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.erreurs.length()").value(3))
                .andExpect(jsonPath("$.erreurs[0].champ").value("age"))
                .andExpect(jsonPath("$.erreurs[0].message").value("L'âge minimum est 18 ans"))
                .andExpect(jsonPath("$.erreurs[1].champ").value("email"))
                .andExpect(jsonPath("$.erreurs[2].champ").value("prenom"));
    }

    @Test
    void corpsIllisibleRenvoie400EnFrancais() throws Exception {
        mockMvc.perform(post("/api/essai/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ ceci n'est pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requête invalide"))
                .andExpect(jsonPath("$.detail").value("La requête est mal formée."))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void methodeNonAutoriseeRenvoie405EnFrancais() throws Exception {
        mockMvc.perform(post("/api/essai/panne"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Méthode non autorisée"));
    }

    // -----------------------------------------------------------------
    // Contrôleur d'essai, utilisé uniquement par ce test
    // -----------------------------------------------------------------

    record InscriptionEssai(
            @NotBlank(message = "L'e-mail est obligatoire") @Email(message = "L'e-mail n'est pas valide") String email,
            @NotBlank(message = "Le prénom est obligatoire") String prenom,
            @Min(value = 18, message = "L'âge minimum est 18 ans") int age) {
    }

    @RestController
    static class ControleurEssai {

        @GetMapping("/api/essai/produits/{slug}")
        void produit(@PathVariable String slug) {
            throw new RessourceIntrouvableException("Produit", slug);
        }

        @GetMapping("/api/essai/stock")
        void stock() {
            throw new StockInsuffisantException("ASUS-FA507-16-512", 3, 1);
        }

        @GetMapping("/api/essai/code-promo")
        void codePromo() {
            throw new RegleMetierException("CODE_PROMO_EXPIRE", "Ce code promo a expiré.");
        }

        @GetMapping("/api/essai/panne")
        void panne() {
            throw new IllegalStateException("détail technique à ne jamais exposer");
        }

        @PostMapping("/api/essai/inscription")
        void inscription(@Valid @RequestBody InscriptionEssai requete) {
            // Rien : seule la validation est testée.
        }
    }
}
