package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import sn.boutique.administration.securite.ReauthentificationRequise;
import sn.boutique.compte.modele.Role;

/**
 * Matrice des rôles (ADR 0009 §6) vérifiée <strong>route par route</strong> : toute route {@code /api/admin/**} est parcourue
 * avec chaque rôle. Une route ajoutée sans règle explicite tombe dans la règle générale « équipe » : si ce n'est pas voulu, ce
 * test échoue parce que la matrice ci-dessous ne la prévoit pas.
 */
class MatriceAdministrationTest extends AuthentificationTestBase {

    private static final Set<Role> EQUIPE = Set.of(Role.SERVICE_CLIENT, Role.GESTIONNAIRE_STOCK, Role.ADMIN, Role.SUPER_ADMIN);
    private static final Set<Role> ADMINISTRATION = Set.of(Role.ADMIN, Role.SUPER_ADMIN);
    private static final Set<Role> MEDIAS = Set.of(Role.GESTIONNAIRE_STOCK, Role.ADMIN, Role.SUPER_ADMIN);
    private static final Set<Role> SUPER = Set.of(Role.SUPER_ADMIN);

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    /** Qui a le droit d'appeler la route ? Écrit à la main : c'est la matrice de l'ADR, pas un reflet du code. */
    static Set<Role> autorises(HttpMethod methode, String chemin) {
        if (chemin.startsWith("/api/admin/utilisateurs") || chemin.startsWith("/api/admin/journal")) {
            return SUPER;
        }
        if (chemin.startsWith("/api/admin/variantes") || chemin.startsWith("/api/admin/parametres")
                || chemin.startsWith("/api/admin/bannieres")
                || (methode == HttpMethod.POST && chemin.matches("/api/admin/factures/[^/]+/avoirs"))) {
            return ADMINISTRATION;
        }
        if (chemin.matches("/api/admin/produits/[^/]+/medias.*") || chemin.startsWith("/api/admin/medias")) {
            return MEDIAS;
        }
        return EQUIPE;
    }

    /** Les routes qui exigent une réauthentification : un choix visible, à modifier ici en le sachant. */
    static final Set<String> REAUTHENTIFICATION = Set.of(
            "AdminUtilisateurControleur#inviter", "AdminUtilisateurControleur#renvoyerInvitation",
            "AdminUtilisateurControleur#changerRole", "AdminUtilisateurControleur#desactiver",
            "AdminUtilisateurControleur#activer", "AdminUtilisateurControleur#reinitialiserSecondFacteur",
            "AdminAvoirControleur#emettre", "AdminMentionsFactureControleur#modifier",
            "ControleurJournalDeTest#sensible");

    record Route(HttpMethod methode, String chemin, HandlerMethod handler) {
        String nom() {
            return handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
        }
    }

    private List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entree : mappings.getHandlerMethods().entrySet()) {
            var patterns = entree.getKey().getPathPatternsCondition();
            if (patterns == null) {
                continue;
            }
            for (var pattern : patterns.getPatterns()) {
                String chemin = pattern.getPatternString();
                if (!chemin.startsWith("/api/admin") || chemin.startsWith(ControleurJournalDeTest.PREFIXE)) {
                    continue;
                }
                for (var methode : entree.getKey().getMethodsCondition().getMethods()) {
                    routes.add(new Route(HttpMethod.valueOf(methode.name()), chemin, entree.getValue()));
                }
            }
        }
        return routes;
    }

    private MockHttpServletRequestBuilder requete(Route route, String acces) {
        String chemin = route.chemin().replaceAll("\\{[^}]+}", "0");
        return MockMvcRequestBuilders.request(route.methode(), chemin).with(depuisIpClient())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + acces);
    }

    @Test
    void chaqueRouteDAdministrationRepondAvecLaMatriceDesRoles() throws Exception {
        List<Route> routes = routes();
        assertThat(routes).as("routes d'administration trouvées").hasSizeGreaterThan(20);

        Map<Role, String> acces = new java.util.EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            acces.put(role, jetonAcces(creerEtConnecter(role)));
        }
        List<String> ecarts = new ArrayList<>();
        for (Route route : routes) {
            Set<Role> permis = autorises(route.methode(), route.chemin());
            for (Role role : Role.values()) {
                MvcResult resultat = mockMvc.perform(requete(route, acces.get(role))).andReturn();
                int statut = resultat.getResponse().getStatus();
                boolean refuse = statut == 403 && !resultat.getResponse().getContentAsString().contains("REAUTHENTIFICATION_REQUISE");
                if (permis.contains(role) == refuse) {
                    ecarts.add("%s %s avec %s : statut %d, attendu %s".formatted(route.methode(), route.chemin(), role, statut,
                            permis.contains(role) ? "autorisé" : "403"));
                }
            }
        }
        assertThat(ecarts).as("écarts avec la matrice").isEmpty();
    }

    @Test
    void sansJetonToutesLesRoutesDAdministrationRepondent401() throws Exception {
        List<String> ecarts = new ArrayList<>();
        for (Route route : routes()) {
            int statut = mockMvc.perform(MockMvcRequestBuilders.request(route.methode(),
                    route.chemin().replaceAll("\\{[^}]+}", "0")).with(depuisIpClient())).andReturn().getResponse().getStatus();
            if (statut != 401) {
                ecarts.add(route.methode() + " " + route.chemin() + " : " + statut);
            }
        }
        assertThat(ecarts).isEmpty();
    }

    @Test
    void lesRoutesQuiExigentUneReauthentificationSontCellesDeLaListe() {
        Set<String> marquees = new TreeSet<>();
        for (var entree : mappings.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entree.getValue();
            boolean marque = AnnotatedElementUtils.hasAnnotation(handler.getMethod(), ReauthentificationRequise.class)
                    || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), ReauthentificationRequise.class);
            if (marque) {
                marquees.add(handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName());
            }
        }
        assertThat(marquees).containsExactlyInAnyOrderElementsOf(REAUTHENTIFICATION);
    }

    @Test
    void touteRouteDEcritureDAdministrationDeLaMatriceEstConnue() {
        // Les routes qui écrivent sont les seules à produire des lignes de journal : on les recense pour que l'ajout
        // d'une route d'écriture soit visible (elle sera tracée par la ligne générique, puis détaillée dans son service).
        Set<String> ecritures = new TreeSet<>();
        for (Route route : routes()) {
            if (route.methode() != HttpMethod.GET) {
                ecritures.add(route.methode() + " " + route.chemin());
            }
        }
        assertThat(ecritures).contains(
                "POST /api/admin/commandes/{numero}/confirmer", "POST /api/admin/commandes/{numero}/annuler",
                "POST /api/admin/commandes/{numero}/livrer", "POST /api/admin/factures/{numero}/avoirs",
                "PUT /api/admin/parametres/facturation", "PATCH /api/admin/variantes/{id}",
                "POST /api/admin/utilisateurs", "POST /api/admin/reauthentification");
    }
}
