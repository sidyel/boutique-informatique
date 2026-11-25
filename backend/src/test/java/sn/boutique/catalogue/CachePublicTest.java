package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import sn.boutique.noyau.config.CachePublicConfig;

/**
 * Lectures publiques : transmissibles par le rendu serveur d'Angular (aucun {@code no-store}, {@code no-cache}
 * ni {@code private}), toujours revalidées, avec un ETag pour répondre 304 sans corps. Le reste de l'API
 * (compte, authentification, administration, et plus tard panier et commandes) garde {@code no-store}.
 */
class CachePublicTest extends CatalogueTestBase {

    private static final String REVALIDATION_PUBLIQUE = "max-age=0, must-revalidate, public";

    /** Préfixes des réponses propres à un visiteur : jamais publiques, jamais d'ETag partagé. */
    private static final Pattern ROUTES_PERSONNALISEES =
            Pattern.compile("^/api/(compte|admin|auth|panier|commandes)(/.*)?$");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping routes;

    @Test
    void lesLecturesPubliquesSontRevalideesMaisTransmissiblesAuNavigateur() throws Exception {
        for (String url : new String[] {"/api/parametres", "/api/categories", "/api/marques", "/api/accueil",
                "/api/bannieres?emplacement=ACCUEIL_CARROUSEL", "/api/produits", "/api/recherche/suggestions?q=pc"}) {
            mockMvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, REVALIDATION_PUBLIQUE))
                    .andExpect(header().exists(HttpHeaders.ETAG));
        }
    }

    @Test
    void uneRevalidationSansChangementRepond304SansCorpsMemeAvecUnEtagFaible() throws Exception {
        String etag = mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).startsWith("\"");

        mockMvc.perform(get("/api/categories").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
        // nginx transforme l'ETag en ETag faible quand il compresse : le navigateur renvoie W/"…"
        mockMvc.perform(get("/api/categories").header(HttpHeaders.IF_NONE_MATCH, "W/" + etag))
                .andExpect(status().isNotModified());
        // ETag périmé : la réponse complète est renvoyée
        mockMvc.perform(get("/api/categories").header(HttpHeaders.IF_NONE_MATCH, "\"perime\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @Test
    void leCompteLAuthentificationEtLAdministrationGardentNoStoreSansEtag() throws Exception {
        mockMvc.perform(get("/api/compte/profil"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
        mockMvc.perform(get("/api/admin/produits"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
        mockMvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"inconnu@exemple.sn\",\"motDePasse\":\"mauvais-mot-de-passe\"}"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, not(containsString("public"))))
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
    }

    /** Garde-fou : une route personnalisée ajoutée plus tard ne peut pas tomber dans les listes publiques. */
    @Test
    void aucuneRoutePersonnaliseeNEstCouverteParLesListesPubliques() {
        AntPathMatcher comparateur = new AntPathMatcher();
        List<String> chemins = new ArrayList<>();
        routes.getHandlerMethods().forEach((info, methode) -> {
            if (info.getPathPatternsCondition() != null) {
                chemins.addAll(info.getPathPatternsCondition().getPatternValues());
            }
        });
        assertThat(chemins).as("routes de l'application").anyMatch(c -> c.startsWith("/api/compte"));

        for (String chemin : chemins) {
            if (!ROUTES_PERSONNALISEES.matcher(chemin).matches()) {
                continue;
            }
            for (String motif : CachePublicConfig.lecturesPubliques()) {
                assertThat(comparateur.match(motif, chemin)).as("%s couvert par %s", chemin, motif).isFalse();
            }
            for (String motif : CachePublicConfig.motifsFiltre()) {
                String prefixe = motif.endsWith("/*") ? motif.substring(0, motif.length() - 2) : motif;
                boolean couvert = motif.endsWith("/*")
                        ? chemin.equals(prefixe) || chemin.startsWith(prefixe + "/")
                        : chemin.equals(motif);
                assertThat(couvert).as("%s couvert par le filtre ETag %s", chemin, motif).isFalse();
            }
        }
    }
}
