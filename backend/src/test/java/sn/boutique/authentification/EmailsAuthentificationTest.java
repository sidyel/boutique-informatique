package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.compte.modele.Role;
import sn.boutique.support.ClientMailpit;
import sn.boutique.support.ClientMailpit.Email;

/** Vérification de l'e-mail et mot de passe oublié, avec les vrais e-mails reçus par Mailpit. */
class EmailsAuthentificationTest extends AuthentificationTestBase {

    @Value("${test.mailpit.url}")
    private String urlMailpit;
    @Autowired
    private JwtDecoder decodeurJwt;

    private ClientMailpit mailpit;

    @BeforeEach
    void connecterMailpit() {
        mailpit = new ClientMailpit(urlMailpit);
    }

    // ------------------------------------------------------------------
    // Vérification de l'e-mail
    // ------------------------------------------------------------------

    @Test
    void lInscriptionEnvoieUnLienQuiVerifieLAdresse() throws Exception {
        String email = emailUnique();
        inscrire(email, MOT_DE_PASSE).andExpect(status().isAccepted());

        Email recu = mailpit.dernier(email);
        assertThat(recu.sujet()).isEqualTo("Confirmez votre adresse e-mail");
        assertThat(recu.expediteur()).isEqualTo("no-reply@exemple.sn");
        assertThat(recu.html())
                .contains("Bonjour <span>Awa</span>", "Confirmer mon adresse", "24 heures",
                        "http://localhost:4200/compte/verifier-email?jeton=")
                .doesNotContain("<!--", "th:text", "th:href");

        MvcResult connexion = connecter(email, MOT_DE_PASSE).andExpect(status().isOk()).andReturn();
        verifier(recu.jeton())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Votre adresse e-mail est confirmée."));

        profil(jetonAcces(connexion)).andExpect(jsonPath("$.emailVerifie").value(true));
        // Le JWT suivant (rafraîchissement) porte emailVerifie=true
        MvcResult rafraichi = rafraichir(jetonRafraichissement(connexion)).andExpect(status().isOk()).andReturn();
        assertThat(decodeurJwt.decode(jetonAcces(rafraichi)).getClaimAsBoolean("emailVerifie")).isTrue();

        // Usage unique
        verifier(recu.jeton()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("JETON_INVALIDE"));
    }

    @Test
    void leLienDeVerificationExpireApresVingtQuatreHeures() throws Exception {
        String email = emailUnique();
        inscrire(email, MOT_DE_PASSE);
        String jeton = mailpit.dernier(email).jeton();

        horloge.avancer(Duration.ofHours(24).plusSeconds(1));
        verifier(jeton).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("JETON_INVALIDE"));
    }

    @Test
    void unJetonInventeEstRefuse() throws Exception {
        verifier("a".repeat(43)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("JETON_INVALIDE"));
        verifier("").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void leRenvoiInvalideLAncienLienEtNeRevelePasLesComptes() throws Exception {
        String email = emailUnique();
        inscrire(email, MOT_DE_PASSE);
        String premierJeton = mailpit.dernier(email).jeton();

        String reponseCompteExistant = demander("/api/auth/renvoyer-verification", email)
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String inconnu = emailUnique();
        String reponseCompteInconnu = demander("/api/auth/renvoyer-verification", inconnu)
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(reponseCompteInconnu).isEqualTo(reponseCompteExistant);

        String secondJeton = mailpit.attendre(email, 2).getFirst().jeton();
        assertThat(secondJeton).isNotEqualTo(premierJeton);
        verifier(premierJeton).andExpect(status().isBadRequest());
        verifier(secondJeton).andExpect(status().isOk());
        assertThat(mailpit.compterApresDelai(inconnu)).isZero();

        // Adresse déjà vérifiée : aucun nouvel e-mail
        demander("/api/auth/renvoyer-verification", email).andExpect(status().isAccepted());
        assertThat(mailpit.compterApresDelai(email)).isEqualTo(2);
    }

    @Test
    void uneInscriptionAvecUnEmailExistantPrevientLeTitulaireSansLien() throws Exception {
        String email = emailUnique();
        inscrire(email, MOT_DE_PASSE);
        mailpit.dernier(email);

        inscrire(email, "Autre-mot-de-passe-1").andExpect(status().isAccepted());

        Email recu = mailpit.attendre(email, 2).getFirst();
        assertThat(recu.sujet()).isEqualTo("Vous avez déjà un compte");
        assertThat(recu.html())
                .contains("aucun nouveau compte n'a été créé", "http://localhost:4200/connexion",
                        "http://localhost:4200/mot-de-passe-oublie")
                .doesNotContain("jeton=");
    }

    // ------------------------------------------------------------------
    // Mot de passe oublié
    // ------------------------------------------------------------------

    @Test
    void motDePasseOubliePuisReinitialisation() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.CLIENT);
        String email = corps(connexion).get("utilisateur").get("email").asString();
        String ancienneSession = jetonRafraichissement(connexion);
        // Compte verrouillé par 5 échecs : la réinitialisation le débloque
        for (int i = 0; i < 5; i++) {
            connecter(email, "mauvais-mot-de-passe");
        }

        demander("/api/auth/mot-de-passe-oublie", email).andExpect(status().isAccepted());
        Email recu = mailpit.dernier(email);
        assertThat(recu.sujet()).isEqualTo("Choisissez un nouveau mot de passe");
        assertThat(recu.html()).contains("1 heure", "http://localhost:4200/compte/nouveau-mot-de-passe?jeton=");

        reinitialiser(recu.jeton(), "Nouveau-mot-de-passe-2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Votre mot de passe a été modifié. Vous pouvez vous connecter."));

        // Toutes les sessions sont révoquées, l'ancien mot de passe ne marche plus, le nouveau oui
        rafraichir(ancienneSession).andExpect(status().isUnauthorized());
        connecter(email, MOT_DE_PASSE).andExpect(status().isUnauthorized());
        connecter(email, "Nouveau-mot-de-passe-2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utilisateur.emailVerifie").value(true));

        // Usage unique, et alerte envoyée au titulaire
        reinitialiser(recu.jeton(), "Encore-un-autre-2026").andExpect(status().isBadRequest());
        assertThat(mailpit.attendre(email, 2).getFirst().sujet()).isEqualTo("Votre mot de passe a été modifié");
    }

    @Test
    void motDePasseOublieRepondLaMemeChosePourUnEmailInconnu() throws Exception {
        String email = corps(creerEtConnecter(Role.CLIENT)).get("utilisateur").get("email").asString();
        String inconnu = emailUnique();

        MvcResult existant = demander("/api/auth/mot-de-passe-oublie", email).andReturn();
        MvcResult absent = demander("/api/auth/mot-de-passe-oublie", inconnu).andReturn();

        assertThat(absent.getResponse().getStatus()).isEqualTo(202).isEqualTo(existant.getResponse().getStatus());
        assertThat(absent.getResponse().getContentAsString()).isEqualTo(existant.getResponse().getContentAsString());
        mailpit.dernier(email);
        assertThat(mailpit.compterApresDelai(inconnu)).isZero();
    }

    @Test
    void leLienDeReinitialisationExpireApresUneHeureEtLeDernierSeulCompte() throws Exception {
        String email = corps(creerEtConnecter(Role.CLIENT)).get("utilisateur").get("email").asString();
        demander("/api/auth/mot-de-passe-oublie", email);
        String premier = mailpit.dernier(email).jeton();
        demander("/api/auth/mot-de-passe-oublie", email);
        String second = mailpit.attendre(email, 2).getFirst().jeton();

        reinitialiser(premier, "Nouveau-mot-de-passe-2026").andExpect(status().isBadRequest());

        horloge.avancer(Duration.ofHours(1).plusSeconds(1));
        reinitialiser(second, "Nouveau-mot-de-passe-2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JETON_INVALIDE"));
        connecter(email, MOT_DE_PASSE).andExpect(status().isOk());
    }

    @Test
    void leNouveauMotDePasseDoitEtreRobuste() throws Exception {
        String email = corps(creerEtConnecter(Role.CLIENT)).get("utilisateur").get("email").asString();
        demander("/api/auth/mot-de-passe-oublie", email);
        String jeton = mailpit.dernier(email).jeton();

        reinitialiser(jeton, "court")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("motDePasse"));
        // Le jeton n'a pas été consommé par la requête invalide
        reinitialiser(jeton, "Nouveau-mot-de-passe-2026").andExpect(status().isOk());
    }

    private ResultActions verifier(String jeton) throws Exception {
        return mockMvc.perform(post("/api/auth/verification-email")
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("jeton", jeton))));
    }

    private ResultActions demander(String route, String email) throws Exception {
        return mockMvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email))));
    }

    private ResultActions reinitialiser(String jeton, String motDePasse) throws Exception {
        return mockMvc.perform(post("/api/auth/reinitialiser").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("jeton", jeton, "motDePasse", motDePasse))));
    }
}
