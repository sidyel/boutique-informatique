package sn.boutique;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Vérifie que l'application démarre sur une vraie base PostgreSQL et un vrai Redis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemarrageApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void lesMigrationsFlywayInstallentLesExtensionsDeRecherche() {
        List<String> extensions = jdbcTemplate.queryForList(
                "SELECT extname FROM pg_extension WHERE extname IN ('pg_trgm', 'unaccent')", String.class);

        assertThat(extensions).containsExactlyInAnyOrder("pg_trgm", "unaccent");
    }

    @Test
    void uneRouteInconnueRenvoieUnProblemDetail404EnFrancais() throws Exception {
        mockMvc.perform(get("/api/route-inexistante"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Ressource introuvable"))
                .andExpect(jsonPath("$.detail").value("La ressource demandée n'existe pas."))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/route-inexistante"))
                .andExpect(jsonPath("$.horodatage").exists());
    }

    @Test
    void lAdministrationEstFermeeTantQueLAuthentificationNExistePas() throws Exception {
        mockMvc.perform(get("/api/admin/produits"))
                .andExpect(status().isForbidden());
    }

    @Test
    void laSondeDeSanteEstAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void laDocumentationOpenApiEstPublieeEnDeveloppement() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("API Boutique informatique"));
    }
}
