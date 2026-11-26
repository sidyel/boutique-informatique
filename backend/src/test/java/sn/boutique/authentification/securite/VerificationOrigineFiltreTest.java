package sn.boutique.authentification.securite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import sn.boutique.noyau.config.SecuriteConfig;
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

    @Test
    void protegerLePanierEtLaCommandeSurToutesLesMethodesQuiModifientUnEtat() throws Exception {
        VerificationOrigineFiltre filtreDuSite = new VerificationOrigineFiltre(List.of("https://boutique.sn"),
                SecuriteConfig.ROUTES_AVEC_COOKIE,
                new ReponsesSecurite(JsonMapper.builder().build(), new ProblemesHttp(Clock.systemUTC())));
        for (String methode : List.of("POST", "PUT", "PATCH", "DELETE")) {
            for (String chemin : List.of("/api/panier", "/api/panier/lignes", "/api/panier/lignes/12",
                    "/api/panier/code-promo", "/api/commandes", "/api/commandes/CMD-2026-000001/annuler")) {
                assertThat(executer(filtreDuSite, methode, chemin, null).getStatus())
                        .as("%s %s sans Origin", methode, chemin).isEqualTo(403);
                assertThat(executer(filtreDuSite, methode, chemin, "https://site-pirate.com").getStatus())
                        .as("%s %s depuis un autre site", methode, chemin).isEqualTo(403);
                assertThat(executer(filtreDuSite, methode, chemin, "https://boutique.sn").getStatus())
                        .as("%s %s depuis le site", methode, chemin).isEqualTo(200);
            }
        }
        // Lectures non filtrées ; préfixe strict (pas /api/paniers) ; routes sans cookie non concernées
        assertThat(executer(filtreDuSite, "GET", "/api/panier", null).getStatus()).isEqualTo(200);
        assertThat(executer(filtreDuSite, "HEAD", "/api/commandes/suivi", null).getStatus()).isEqualTo(200);
        assertThat(executer(filtreDuSite, "POST", "/api/paniers", null).getStatus()).isEqualTo(200);
        assertThat(executer(filtreDuSite, "POST", "/api/paiements/paydunya/webhook", null).getStatus()).isEqualTo(200);
        assertThat(executer(filtreDuSite, "POST", "/api/compte/commandes", null).getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse executer(String methode, String chemin, String origine) throws Exception {
        return executer(filtre, methode, chemin, origine);
    }

    private static MockHttpServletResponse executer(VerificationOrigineFiltre filtre, String methode, String chemin,
                                                    String origine) throws Exception {
        MockHttpServletRequest requete = new MockHttpServletRequest(methode, chemin);
        if (origine != null) {
            requete.addHeader("Origin", origine);
        }
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete, reponse, new MockFilterChain());
        return reponse;
    }
}
