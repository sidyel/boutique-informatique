package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.support.ClientMailpit;
import sn.boutique.support.ClientMailpit.Email;

/**
 * Relecture de sécurité I1, M1 et M2 (ADR 0003 §8 et §9) : verrouillage et quotas par couple (e-mail, IP),
 * lien de déverrouillage, chemin identique pour un e-mail inconnu, quota par e-mail sans 429.
 */
class VerrouillageParEmailEtIpTest extends AuthentificationTestBase {

    private static final String MAUVAIS = "mauvais-mot-de-passe";

    @Value("${test.mailpit.url}")
    private String urlMailpit;

    private ClientMailpit mailpit;
    private String ipAttaquant;

    @BeforeEach
    void preparer() {
        mailpit = new ClientMailpit(urlMailpit);
        ipAttaquant = "196.207." + (1 + (int) (Math.random() * 250)) + "." + (1 + (int) (Math.random() * 250));
    }

    // --- I1 : la victime n'est pas bloquée par l'attaquant ------------------------------------

    @Test
    void cinqEchecsDepuisUneIpNeBloquentPasLeTitulaireDepuisUneAutreIp() throws Exception {
        String email = emailDuCompte(creerEtConnecter(Role.ADMIN));

        for (int i = 0; i < 5; i++) {
            connecterDepuis(ipAttaquant, email, MAUVAIS).andExpect(status().isUnauthorized());
        }
        // Depuis l'IP de l'attaquant : verrouillé, même avec le bon mot de passe, réponse générique
        String verrouille = connecterDepuis(ipAttaquant, email, MOT_DE_PASSE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"))
                .andReturn().getResponse().getContentAsString();
        String inconnu = connecterDepuis(ipAttaquant, emailUnique(), MOT_DE_PASSE).andReturn().getResponse()
                .getContentAsString();
        assertThat(sansHorodatage(verrouille)).isEqualTo(sansHorodatage(inconnu));

        // Le titulaire, depuis son IP, se connecte normalement
        connecter(email, MOT_DE_PASSE).andExpect(status().isOk());
    }

    @Test
    void leTitulaireRecoitUnSeulLienQuiLeveTousLesVerrouillages() throws Exception {
        String email = emailDuCompte(creerEtConnecter(Role.CLIENT));
        String autreIpAttaquant = ipAttaquant.replace("196.207.", "196.209.");
        for (String ip : new String[] {ipAttaquant, autreIpAttaquant}) {
            for (int i = 0; i < 5; i++) {
                connecterDepuis(ip, email, MAUVAIS);
            }
        }

        Email recu = mailpit.dernier(email);
        assertThat(recu.sujet()).isEqualTo("Connexion bloquée sur votre compte");
        assertThat(recu.html()).contains("Débloquer mon compte", "1 heure", "15 minutes",
                "http://localhost:4200/compte/deverrouiller?jeton=", "http://localhost:4200/mot-de-passe-oublie");
        assertThat(mailpit.compterApresDelai(email)).as("un seul e-mail tant que le lien est valable").isEqualTo(1);

        deverrouiller(recu.jeton())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Votre compte est débloqué. Vous pouvez vous connecter."));
        connecterDepuis(ipAttaquant, email, MOT_DE_PASSE).andExpect(status().isOk());
        connecterDepuis(autreIpAttaquant, email, MOT_DE_PASSE).andExpect(status().isOk());
        // Usage unique
        deverrouiller(recu.jeton()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JETON_INVALIDE"));
    }

    @Test
    void lePremierLienExpireUnNouveauVerrouillageEnEnvoieUnAutre() throws Exception {
        String email = emailDuCompte(creerEtConnecter(Role.CLIENT));
        for (int i = 0; i < 5; i++) {
            connecterDepuis(ipAttaquant, email, MAUVAIS);
        }
        String premier = mailpit.dernier(email).jeton();

        horloge.avancer(java.time.Duration.ofMinutes(61));
        for (int i = 0; i < 5; i++) {
            connecterDepuis(ipAttaquant, email, MAUVAIS);
        }
        assertThat(mailpit.attendre(email, 2)).hasSize(2);
        deverrouiller(premier).andExpect(status().isBadRequest());
    }

    // --- M1 : même chemin, même écriture, que le compte existe ou non --------------------------

    @Test
    void unEmailInconnuSuitExactementLeMemeChemin() throws Exception {
        String existant = emailDuCompte(creerEtConnecter(Role.CLIENT));
        String inconnu = emailUnique();

        String reponseExistant = connecterDepuis(ipAttaquant, existant, MAUVAIS).andReturn().getResponse()
                .getContentAsString();
        String reponseInconnu = connecterDepuis(ipAttaquant, inconnu, MAUVAIS).andReturn().getResponse()
                .getContentAsString();
        assertThat(sansHorodatage(reponseExistant)).isEqualTo(sansHorodatage(reponseInconnu));
        // La même ligne est écrite dans les deux cas (empreinte de l'adresse saisie, jamais l'adresse en clair)
        assertThat(echecs(existant)).isEqualTo(1);
        assertThat(echecs(inconnu)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from echec_connexion where empreinte_email like '%@%'",
                Integer.class)).isZero();

        for (int i = 0; i < 4; i++) {
            connecterDepuis(ipAttaquant, existant, MAUVAIS);
            connecterDepuis(ipAttaquant, inconnu, MAUVAIS);
        }
        assertThat(verrouille(existant)).isTrue();
        assertThat(verrouille(inconnu)).isTrue();
    }

    // --- Quota de connexion par (e-mail, IP) -----------------------------------------------------

    @Test
    void leQuotaDeConnexionParEmailEtIpNeBloqueQueLIpConcernee() throws Exception {
        String email = emailDuCompte(creerEtConnecter(Role.CLIENT));
        for (int i = 1; i <= 10; i++) {
            connecterDepuis(ipAttaquant, email, MAUVAIS).andExpect(status().isUnauthorized());
        }
        connecterDepuis(ipAttaquant, email, MOT_DE_PASSE).andExpect(status().isTooManyRequests());

        connecter(email, MOT_DE_PASSE).andExpect(status().isOk());
    }

    // --- M2 : mot de passe oublié et renvoi de la vérification ----------------------------------

    @Test
    void motDePasseOublieQuotaParEmailEtIpPuisParEmailSans429NiEnvoi() throws Exception {
        String email = emailDuCompte(creerEtConnecter(Role.CLIENT));
        verifierQuotas("/api/auth/mot-de-passe-oublie", email, 0);
    }

    @Test
    void renvoiVerificationQuotaParEmailEtIpPuisParEmailSans429NiEnvoi() throws Exception {
        String email = emailUnique();
        inscrire(email, MOT_DE_PASSE).andExpect(status().isAccepted());
        mailpit.attendre(email, 1);
        verifierQuotas("/api/auth/renvoyer-verification", email, 1);
    }

    /**
     * 3 demandes par (e-mail, IP), puis 429 pour cette IP seulement. Depuis d'autres IP, jusqu'à 10 demandes
     * par e-mail et par heure ; au-delà, toujours 202, mais plus aucun e-mail.
     */
    private void verifierQuotas(String route, String email, int emailsDejaRecus) throws Exception {
        for (int i = 1; i <= 3; i++) {
            demanderDepuis(ipAttaquant, route, email).andExpect(status().isAccepted());
        }
        demanderDepuis(ipAttaquant, route, email).andExpect(status().isTooManyRequests());

        int acceptees = 3;
        int ip = 1;
        while (acceptees < 10) {
            demanderDepuis("41.82.77." + ip++, route, email).andExpect(status().isAccepted());
            acceptees++;
        }
        assertThat(mailpit.attendre(email, emailsDejaRecus + 10)).hasSize(emailsDejaRecus + 10);

        // Au-delà du quota de l'adresse : même réponse 202, rien n'est envoyé
        for (int i = 0; i < 3; i++) {
            demanderDepuis("41.82.78." + i, route, email)
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").exists());
        }
        assertThat(mailpit.compterApresDelai(email)).isEqualTo(emailsDejaRecus + 10);
    }

    // ------------------------------------------------------------------------------------------------

    private ResultActions connecterDepuis(String ip, String email, String motDePasse) throws Exception {
        return mockMvc.perform(post("/api/auth/connexion").with(requete -> {
                    requete.setRemoteAddr(ip);
                    return requete;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "motDePasse", motDePasse))));
    }

    private ResultActions demanderDepuis(String ip, String route, String email) throws Exception {
        return mockMvc.perform(post(route).with(requete -> {
                    requete.setRemoteAddr(ip);
                    return requete;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email))));
    }

    private ResultActions deverrouiller(String jeton) throws Exception {
        return mockMvc.perform(post("/api/auth/deverrouiller").with(depuisIpClient())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("jeton", jeton))));
    }

    private String emailDuCompte(MvcResult connexion) throws Exception {
        return corps(connexion).get("utilisateur").get("email").asString();
    }

    private Integer echecs(String email) {
        return jdbc.queryForObject("select echecs from echec_connexion where empreinte_email = ? and adresse_ip = ?",
                Integer.class, empreinte(email), ipAttaquant);
    }

    private boolean verrouille(String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select verrouille_jusqua is not null from echec_connexion "
                + "where empreinte_email = ? and adresse_ip = ?", Boolean.class, empreinte(email), ipAttaquant));
    }

    private static String empreinte(String email) {
        return GenerateurJetons.empreinte(Utilisateur.normaliserEmail(email));
    }

    private static String sansHorodatage(String json) {
        return json.replaceAll("\"horodatage\":\"[^\"]*\"", "");
    }
}
