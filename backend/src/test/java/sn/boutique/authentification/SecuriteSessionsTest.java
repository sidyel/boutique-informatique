package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;

/** Rotation, réutilisation, CSRF, durées de session et verrouillage du compte. */
class SecuriteSessionsTest extends AuthentificationTestBase {

    @Test
    void reutiliserUnJetonDejaUtiliseRevoqueToutLaFamille() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.CLIENT);
        String jetonA = jetonRafraichissement(connexion);
        String jetonB = jetonRafraichissement(rafraichir(jetonA).andExpect(status().isOk()).andReturn());

        // Un voleur (ou la victime) présente l'ancien jeton A : 401 et cookie effacé…
        MvcResult reutilisation = rafraichir(jetonA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALIDE"))
                .andReturn();
        assertThat(setCookie(reutilisation)).contains("Max-Age=0");

        // … et le jeton B, pourtant jamais utilisé, est révoqué aussi : les deux parties doivent se reconnecter
        rafraichir(jetonB).andExpect(status().isUnauthorized());
        assertThat(jetonsActifs(idUtilisateur(connexion))).isZero();
    }

    @Test
    void laReutilisationNeTouchePasLesAutresSessionsDuCompte() throws Exception {
        MvcResult telephone = creerEtConnecter(Role.CLIENT);
        String email = corps(telephone).get("utilisateur").get("email").asString();
        MvcResult ordinateur = connecter(email, MOT_DE_PASSE).andReturn();

        String jetonTelephone = jetonRafraichissement(telephone);
        rafraichir(jetonTelephone).andExpect(status().isOk());
        rafraichir(jetonTelephone).andExpect(status().isUnauthorized());

        rafraichir(jetonRafraichissement(ordinateur)).andExpect(status().isOk());
    }

    @Test
    void unJetonInconnuOuAbsentEstRefuse() throws Exception {
        rafraichir("jeton-invente-de-toutes-pieces")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALIDE"));
        mockMvc.perform(post("/api/auth/rafraichir").with(depuisIpClient()).header(HttpHeaders.ORIGIN, ORIGINE_SITE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rafraichirSansOriginOuDepuisUneAutreOrigineEstRefuseSansConsommerLeJeton() throws Exception {
        String jeton = jetonRafraichissement(creerEtConnecter(Role.CLIENT));

        mockMvc.perform(post("/api/auth/rafraichir").with(depuisIpClient()).cookie(new Cookie(NOM_COOKIE, jeton)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGINE_REFUSEE"));
        // Même hôte que l'API mais pas l'URL du site : refusé par la vérification d'origine
        mockMvc.perform(post("/api/auth/rafraichir").with(depuisIpClient()).header(HttpHeaders.ORIGIN, "http://localhost")
                        .cookie(new Cookie(NOM_COOKIE, jeton)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGINE_REFUSEE"));
        // Site tiers et sous-domaine : refusés (dès le contrôle CORS)
        for (String origine : new String[] {"https://site-pirate.com", "http://blog.localhost:4200"}) {
            mockMvc.perform(post("/api/auth/rafraichir").with(depuisIpClient()).header(HttpHeaders.ORIGIN, origine)
                            .cookie(new Cookie(NOM_COOKIE, jeton)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/auth/deconnexion").with(depuisIpClient()).cookie(new Cookie(NOM_COOKIE, jeton)))
                .andExpect(status().isForbidden());

        // Aucune de ces requêtes n'a consommé ni révoqué le jeton
        rafraichir(jeton).andExpect(status().isOk());
    }

    @Test
    void sessionClientDeTrenteJoursGlissante() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.CLIENT);
        assertThat(setCookie(connexion)).contains("Max-Age=2592000");

        horloge.avancer(Duration.ofDays(20));
        MvcResult rafraichissement = rafraichir(jetonRafraichissement(connexion)).andExpect(status().isOk()).andReturn();
        // Repart pour 30 jours
        assertThat(setCookie(rafraichissement)).contains("Max-Age=2592000");

        horloge.avancer(Duration.ofDays(30).plusSeconds(1));
        rafraichir(jetonRafraichissement(rafraichissement)).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionInterneDeDouzeHeuresAbsolue() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        assertThat(setCookie(connexion)).contains("Max-Age=43200");

        horloge.avancer(Duration.ofHours(3));
        MvcResult rafraichissement = rafraichir(jetonRafraichissement(connexion)).andExpect(status().isOk()).andReturn();
        // Le rafraîchissement ne prolonge pas la session : il reste 9 heures
        assertThat(setCookie(rafraichissement)).contains("Max-Age=32400");

        horloge.avancer(Duration.ofHours(9).plusSeconds(1));
        rafraichir(jetonRafraichissement(rafraichissement)).andExpect(status().isUnauthorized());
    }

    @Test
    void unCompteDesactiveNePeutNiRafraichirNiSeConnecter() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.CLIENT);
        Utilisateur utilisateur = utilisateurDepot.findById(idUtilisateur(connexion)).orElseThrow();
        utilisateur.setActif(false);
        utilisateurDepot.save(utilisateur);

        rafraichir(jetonRafraichissement(connexion)).andExpect(status().isUnauthorized());
        assertThat(jetonsActifs(utilisateur.getId())).isZero();
        connecter(utilisateur.getEmail(), MOT_DE_PASSE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"));
    }

    @Test
    void cinqEchecsVerrouillentLeCoupleEmailIpQuinzeMinutes() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.CLIENT);
        String email = corps(connexion).get("utilisateur").get("email").asString();

        for (int i = 0; i < 5; i++) {
            connecter(email, "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        }
        // Verrouillé : même le bon mot de passe est refusé, avec la même réponse qu'un échec ordinaire
        String reponseVerrouille = connecter(email, MOT_DE_PASSE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"))
                .andReturn().getResponse().getContentAsString();
        String reponseEmailInconnu = connecter(emailUnique(), MOT_DE_PASSE).andReturn().getResponse().getContentAsString();
        assertThat(sansHorodatage(reponseVerrouille)).isEqualTo(sansHorodatage(reponseEmailInconnu));

        horloge.avancer(Duration.ofMinutes(14));
        connecter(email, MOT_DE_PASSE).andExpect(status().isUnauthorized());

        horloge.avancer(Duration.ofMinutes(1).plusSeconds(1));
        connecter(email, MOT_DE_PASSE).andExpect(status().isOk());
    }

    @Test
    void uneConnexionReussieRemetLesEchecsAZero() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.CLIENT);
        String email = corps(connexion).get("utilisateur").get("email").asString();
        for (int i = 0; i < 4; i++) {
            connecter(email, "mauvais-mot-de-passe");
        }
        connecter(email, MOT_DE_PASSE).andExpect(status().isOk());
        connecter(email, "mauvais-mot-de-passe");
        // Un seul échec depuis la dernière réussite : pas de verrouillage
        connecter(email, MOT_DE_PASSE).andExpect(status().isOk());
    }

    private static String sansHorodatage(String json) {
        return json.replaceAll("\"horodatage\":\"[^\"]*\"", "");
    }
}
