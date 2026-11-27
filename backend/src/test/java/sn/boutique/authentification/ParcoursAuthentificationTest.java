package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;
import sn.boutique.authentification.evenement.InscriptionAvecEmailExistant;
import sn.boutique.authentification.evenement.InscriptionEffectuee;

/** Parcours nominal : inscription → connexion → profil → rafraîchissement → déconnexion. */
@RecordApplicationEvents
class ParcoursAuthentificationTest extends AuthentificationTestBase {

    @Autowired
    private JwtDecoder decodeurJwt;

    @Test
    void parcoursCompletDeLInscriptionALaDeconnexion(ApplicationEvents evenements) throws Exception {
        String email = emailUnique();

        // 1. Inscription : 202, compte CLIENT non vérifié
        inscrire(email, MOT_DE_PASSE)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "Inscription enregistrée. Consultez votre boîte mail pour confirmer votre adresse."));
        assertThat(evenements.stream(InscriptionEffectuee.class)).hasSize(1);

        // 2. Connexion (casse et espaces de l'e-mail ignorés), avant même la vérification de l'e-mail
        MvcResult connexion = connecter("  " + email.toUpperCase() + " ", MOT_DE_PASSE)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.typeJeton").value("Bearer"))
                .andExpect(jsonPath("$.expireDans").value(900))
                .andExpect(jsonPath("$.utilisateur.email").value(email))
                .andExpect(jsonPath("$.utilisateur.telephone").value("+221771234567"))
                .andExpect(jsonPath("$.utilisateur.role").value("CLIENT"))
                .andExpect(jsonPath("$.utilisateur.emailVerifie").value(false))
                .andExpect(jsonPath("$.jetonRafraichissement").doesNotExist())
                .andReturn();

        assertThat(setCookie(connexion))
                .startsWith(NOM_COOKIE + "=")
                .contains("Path=/api/auth", "Max-Age=2592000", "HttpOnly", "SameSite=Strict");

        // Contenu du JWT
        Jwt jwt = decodeurJwt.decode(jetonAcces(connexion));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(idUtilisateur(connexion)));
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("boutique-informatique");
        assertThat(jwt.getClaimAsString("email")).isEqualTo(email);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CLIENT");
        assertThat(jwt.getClaimAsBoolean("emailVerifie")).isFalse();
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
        assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "iat", "exp", "email", "roles", "emailVerifie", "sid");
        assertThat(java.util.UUID.fromString(jwt.getClaimAsString("sid"))).as("session courante").isNotNull();

        // 3. Profil
        profil(jetonAcces(connexion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prenom").value("Awa"))
                .andExpect(jsonPath("$.consentementMarketing").value(true));

        // 4. Rafraîchissement : nouveau jeton d'accès et nouveau cookie (rotation)
        String premierJeton = jetonRafraichissement(connexion);
        MvcResult rafraichissement = rafraichir(premierJeton)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utilisateur.email").value(email))
                .andReturn();
        String secondJeton = jetonRafraichissement(rafraichissement);
        assertThat(secondJeton).isNotEqualTo(premierJeton).hasSize(43);
        profil(jetonAcces(rafraichissement)).andExpect(status().isOk());

        // 5. Déconnexion : 204, cookie effacé, la session ne peut plus être rafraîchie
        MvcResult deconnexion = mockMvc.perform(post("/api/auth/deconnexion")
                        .header(HttpHeaders.ORIGIN, ORIGINE_SITE)
                        .cookie(new Cookie(NOM_COOKIE, secondJeton)))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(setCookie(deconnexion)).contains(NOM_COOKIE + "=;", "Max-Age=0", "Path=/api/auth");
        assertThat(jetonsActifs(idUtilisateur(connexion))).isZero();

        rafraichir(secondJeton)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALIDE"));
    }

    @Test
    void inscriptionAvecUnEmailDejaUtiliseDonneLaMemeReponse(ApplicationEvents evenements) throws Exception {
        String email = emailUnique();
        MvcResult premiere = inscrire(email, MOT_DE_PASSE).andExpect(status().isAccepted()).andReturn();
        evenements.clear();

        MvcResult seconde = inscrire(email.toUpperCase(), "Autre-mot-de-passe-1").andReturn();

        assertThat(seconde.getResponse().getStatus()).isEqualTo(premiere.getResponse().getStatus());
        assertThat(seconde.getResponse().getContentAsString()).isEqualTo(premiere.getResponse().getContentAsString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM utilisateur WHERE email = ?", Integer.class, email))
                .isOne();
        // Le titulaire sera prévenu ; aucun nouveau compte, et son mot de passe reste inchangé
        assertThat(evenements.stream(InscriptionAvecEmailExistant.class)).hasSize(1);
        assertThat(evenements.stream(InscriptionEffectuee.class)).isEmpty();
        connecter(email, MOT_DE_PASSE).andExpect(status().isOk());
    }

    @Test
    void inscriptionValideLesChamps() throws Exception {
        mockMvc.perform(post("/api/auth/inscription")
                        .contentType("application/json")
                        .content(json(java.util.Map.of("email", "pas-un-email", "motDePasse", "court",
                                "prenom", "", "nom", "Diop", "telephone", "12345"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.erreurs[*].champ").value(
                        org.hamcrest.Matchers.containsInAnyOrder("email", "motDePasse", "prenom", "telephone")));
    }

    @Test
    void motDePasseDePlusDe72OctetsRefuseALInscriptionSansErreurALaConnexion() throws Exception {
        inscrire(emailUnique(), "é".repeat(40)).andExpect(status().isBadRequest());

        MvcResult client = creerEtConnecter(sn.boutique.compte.modele.Role.CLIENT);
        String email = corps(client).get("utilisateur").get("email").asString();
        connecter(email, "x".repeat(200))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"));
    }

    @Test
    void lesRoutesDuCompteExigentUnJeton() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/compte/profil"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"));
    }
}
