package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.authentification.service.JetonAccesService;
import sn.boutique.compte.modele.Role;

/** Réauthentification par code TOTP pour les actions sensibles (ADR 0009 §8). */
class ReauthentificationTest extends AuthentificationTestBase {

    @Autowired
    JetonAccesService jetons;

    private ResultActions sensible(String acces) throws Exception {
        return mockMvc.perform(post(ControleurJournalDeTest.PREFIXE + "/sensible").with(depuisIpClient())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + acces));
    }

    private ResultActions reauthentifier(String acces, String code) throws Exception {
        return mockMvc.perform(post("/api/admin/reauthentification").with(depuisIpClient())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + acces)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("code", code))));
    }

    @Test
    void uneActionSensibleExigeUnCodeRecent() throws Exception {
        String acces = jetonAcces(creerEtConnecter(Role.ADMIN));
        sensible(acces).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTHENTIFICATION_REQUISE"));

        // Le code du login ne peut pas resservir (anti-rejeu) : il faut attendre le pas suivant
        reauthentifier(acces, codeTotp()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_INVALIDE"));
        sensible(acces).andExpect(status().isForbidden());

        horloge.avancer(Duration.ofSeconds(30));
        reauthentifier(acces, codeTotp()).andExpect(status().isOk()).andExpect(jsonPath("$.valideJusqu").isNotEmpty());
        sensible(acces).andExpect(status().isNoContent());
    }

    @Test
    void laFenetreSeFermeAuBoutDeDixMinutes() throws Exception {
        String acces = jetonAcces(creerEtConnecter(Role.SUPER_ADMIN));
        horloge.avancer(Duration.ofSeconds(30));
        reauthentifier(acces, codeTotp()).andExpect(status().isOk());

        horloge.avancer(Duration.ofMinutes(9).plusSeconds(30));
        sensible(acces).andExpect(status().isNoContent());
        horloge.avancer(Duration.ofSeconds(31));
        sensible(acces).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTHENTIFICATION_REQUISE"));
    }

    @Test
    void laFenetreEstLieeALaSession() throws Exception {
        MvcResult premiere = creerEtConnecter(Role.ADMIN);
        String email = corps(premiere).get("utilisateur").get("email").asString();
        horloge.avancer(Duration.ofSeconds(30));
        reauthentifier(jetonAcces(premiere), codeTotp()).andExpect(status().isOk());

        // Le même compte, connecté ailleurs (autre session) : aucune fenêtre
        horloge.avancer(Duration.ofSeconds(30));
        MvcResult autre = connecterCompteInterne(email);
        sensible(jetonAcces(autre)).andExpect(status().isForbidden());
        sensible(jetonAcces(premiere)).andExpect(status().isNoContent());
    }

    @Test
    void unCodeDeSecoursNeConvientPas() throws Exception {
        String acces = jetonAcces(creerEtConnecter(Role.ADMIN));
        reauthentifier(acces, "ABCDE-FGHJK").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_INVALIDE"));
        sensible(acces).andExpect(status().isForbidden());
    }

    @Test
    void unJetonSansSessionNePeutPasOuvrirDeFenetre() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        long id = idUtilisateur(connexion);
        String sansSession = jetons.emettre(utilisateurDepot.findById(id).orElseThrow()).valeur();
        sensible(sansSession).andExpect(status().isForbidden());
        horloge.avancer(Duration.ofSeconds(30));
        reauthentifier(sansSession, codeTotp()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAUTHENTIFICATION_REQUISE"));
    }

    @Test
    void laFenetreSeFermeQuandLeCompteEstDesactive() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        String acces = jetonAcces(connexion);
        horloge.avancer(Duration.ofSeconds(30));
        reauthentifier(acces, codeTotp()).andExpect(status().isOk());
        jdbc.update("update utilisateur set actif = false where id = ?", idUtilisateur(connexion));
        // Le jeton d'accès vit encore 15 minutes, mais l'administration recontrôle le compte à chaque appel
        sensible(acces).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_INVALIDE"));
    }

    @Test
    void unChangementDeRoleInvalideLesJetonsDejaEmis() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        String acces = jetonAcces(connexion);
        jdbc.update("update utilisateur set role = 'SERVICE_CLIENT' where id = ?", idUtilisateur(connexion));
        sensible(acces).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_INVALIDE"));
    }

    @Test
    void lesTentativesManquantesSontJournalisees() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        long id = idUtilisateur(connexion);
        sensible(jetonAcces(connexion)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from journal_administration where utilisateur_id = ? "
                + "and action = 'REAUTHENTIFICATION_MANQUANTE'", Integer.class, id)).isEqualTo(1);
    }
}
