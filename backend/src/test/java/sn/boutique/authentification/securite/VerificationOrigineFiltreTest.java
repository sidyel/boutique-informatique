package sn.boutique.authentification.securite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import sn.boutique.noyau.erreur.ProblemesHttp;
import tools.jackson.databind.json.JsonMapper;

class VerificationOrigineFiltreTest {

    private final VerificationOrigineFiltre filtre = new VerificationOrigineFiltre(
            List.of("https://boutique.sn", "https://www.boutique.sn/"),
            List.of("/api/auth/rafraichir", "/api/auth/deconnexion"),
            new ReponsesSecurite(JsonMapper.builder().build(), new ProblemesHttp(Clock.systemUTC())));

    @Test
    void laisserPasserLOrigineDuSite() throws Exception {
        assertThat(executer("POST", "/api/auth/rafraichir", "https://boutique.sn").getStatus()).isEqualTo(200);
        // Comparaison insensible à la casse et à la barre finale
        assertThat(executer("POST", "/api/auth/deconnexion", "HTTPS://WWW.BOUTIQUE.SN").getStatus()).isEqualTo(200);
    }

    @Test
    void refuserUneRequeteSansOrigin() throws Exception {
        MockHttpServletResponse reponse = executer("POST", "/api/auth/rafraichir", null);

        assertThat(reponse.getStatus()).isEqualTo(403);
        assertThat(reponse.getContentType()).startsWith("application/problem+json");
        assertThat(reponse.getContentAsString()).contains("\"code\":\"ORIGINE_REFUSEE\"");
    }

    @Test
    void refuserUneOrigineEtrangereUnSousDomaineOuUnAutreSchema() throws Exception {
        assertThat(executer("POST", "/api/auth/rafraichir", "https://site-pirate.com").getStatus()).isEqualTo(403);
        assertThat(executer("POST", "/api/auth/rafraichir", "https://blog.boutique.sn").getStatus()).isEqualTo(403);
        assertThat(executer("POST", "/api/auth/rafraichir", "http://boutique.sn").getStatus()).isEqualTo(403);
        assertThat(executer("POST", "/api/auth/rafraichir", "https://boutique.sn.pirate.com").getStatus()).isEqualTo(403);
        assertThat(executer("POST", "/api/auth/deconnexion", "null").getStatus()).isEqualTo(403);
    }

    @Test
    void neConcernePasLesAutresRoutesNiLesAutresMethodes() throws Exception {
        assertThat(executer("POST", "/api/auth/connexion", null).getStatus()).isEqualTo(200);
        assertThat(executer("GET", "/api/auth/rafraichir", null).getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse executer(String methode, String chemin, String origine) throws Exception {
        MockHttpServletRequest requete = new MockHttpServletRequest(methode, chemin);
        if (origine != null) {
            requete.addHeader("Origin", origine);
        }
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete, reponse, new MockFilterChain());
        return reponse;
    }
}
