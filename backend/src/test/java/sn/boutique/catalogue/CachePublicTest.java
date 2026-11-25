package sn.boutique.catalogue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Les lectures publiques sont transmissibles par le rendu serveur d'Angular (aucun {@code no-store},
 * {@code no-cache} ni {@code private}) mais toujours revalidées ; le reste de l'API garde {@code no-store}.
 */
class CachePublicTest extends CatalogueTestBase {

    private static final String REVALIDATION_PUBLIQUE = "max-age=0, must-revalidate, public";

    @Test
    void lesLecturesPubliquesSontRevalideesMaisTransmissiblesAuNavigateur() throws Exception {
        for (String url : new String[] {"/api/parametres", "/api/categories", "/api/marques", "/api/accueil",
                "/api/bannieres?emplacement=ACCUEIL_CARROUSEL", "/api/produits", "/api/recherche/suggestions?q=pc"}) {
            mockMvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, REVALIDATION_PUBLIQUE));
        }
    }

    @Test
    void leCompteEtLAdministrationGardentNoStore() throws Exception {
        mockMvc.perform(get("/api/compte/profil"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(get("/api/admin/produits"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")));
    }
}
