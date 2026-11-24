package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.TestcontainersConfiguration;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.support.HorlogeReglable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Socle des tests d'intégration de l'authentification : vraie base, vrai Redis, horloge réglable. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, HorlogeReglable.Configuration.class})
abstract class AuthentificationTestBase {

    static final String ORIGINE_SITE = "http://localhost:4200";
    static final String MOT_DE_PASSE = "Thieboudienne-2026";
    static final String NOM_COOKIE = "jeton_rafraichissement";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    HorlogeReglable horloge;
    @Autowired
    UtilisateurDepot utilisateurDepot;
    @Autowired
    PasswordEncoder encodeur;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void remettreLHorlogeALHeure() {
        horloge.reinitialiser();
    }

    static String emailUnique() {
        return "awa.diop." + UUID.randomUUID().toString().substring(0, 8) + "@exemple.sn";
    }

    ResultActions inscrire(String email, String motDePasse) throws Exception {
        return mockMvc.perform(post("/api/auth/inscription")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "motDePasse", motDePasse, "prenom", "Awa", "nom", "Diop",
                        "telephone", "77 123 45 67", "consentementMarketing", true))));
    }

    ResultActions connecter(String email, String motDePasse) throws Exception {
        return mockMvc.perform(post("/api/auth/connexion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "motDePasse", motDePasse))));
    }

    ResultActions rafraichir(String jeton) throws Exception {
        return mockMvc.perform(post("/api/auth/rafraichir")
                .header(HttpHeaders.ORIGIN, ORIGINE_SITE)
                .cookie(new Cookie(NOM_COOKIE, jeton)));
    }

    ResultActions profil(String jetonAcces) throws Exception {
        return mockMvc.perform(get("/api/compte/profil").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAcces));
    }

    /** Crée directement un compte (rôle au choix) et renvoie la réponse de connexion. */
    MvcResult creerEtConnecter(Role role) throws Exception {
        String email = emailUnique();
        Utilisateur utilisateur = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE), "Moussa", "Ndiaye");
        utilisateur.setRole(role);
        utilisateurDepot.save(utilisateur);
        MvcResult resultat = connecter(email, MOT_DE_PASSE).andReturn();
        assertThat(resultat.getResponse().getStatus()).isEqualTo(200);
        return resultat;
    }

    String jetonAcces(MvcResult resultat) throws Exception {
        return corps(resultat).get("jetonAcces").asString();
    }

    long idUtilisateur(MvcResult resultat) throws Exception {
        return corps(resultat).get("utilisateur").get("id").asLong();
    }

    static String jetonRafraichissement(MvcResult resultat) {
        Cookie cookie = resultat.getResponse().getCookie(NOM_COOKIE);
        assertThat(cookie).as("cookie de rafraîchissement").isNotNull();
        return cookie.getValue();
    }

    static String setCookie(MvcResult resultat) {
        return resultat.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    }

    JsonNode corps(MvcResult resultat) throws Exception {
        return jsonMapper.readTree(resultat.getResponse().getContentAsString());
    }

    int jetonsActifs(long utilisateurId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM jeton_rafraichissement WHERE utilisateur_id = ? AND NOT revoque AND date_utilisation IS NULL",
                Integer.class, utilisateurId);
    }

    String json(Object objet) {
        return jsonMapper.writeValueAsString(objet);
    }
}
