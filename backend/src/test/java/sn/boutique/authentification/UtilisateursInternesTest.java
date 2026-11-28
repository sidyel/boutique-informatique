package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.administration.utilisateur.UtilisateurInterneService;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.support.ClientMailpit;

/** Comptes de l'équipe : invitation, rôles, désactivation, réinitialisation du second facteur (ADR 0009 §7). */
class UtilisateursInternesTest extends AuthentificationTestBase {

    @Value("${test.mailpit.url}")
    private String urlMailpit;

    private ClientMailpit mailpit;

    @Autowired
    UtilisateurInterneService service;

    @BeforeEach
    void connecterMailpit() {
        mailpit = new ClientMailpit(urlMailpit);
    }

    /** Connexion d'un SUPER_ADMIN, réauthentification faite : prêt à gérer les comptes. */
    private record Session(MvcResult connexion, String acces, long id) {
    }

    private Session superAdminReauthentifie() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.SUPER_ADMIN);
        horloge.avancer(Duration.ofSeconds(30));
        String acces = jetonAcces(connexion);
        mockMvc.perform(post("/api/admin/reauthentification").with(depuisIpClient())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + acces)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("code", codeTotp())))).andExpect(status().isOk());
        return new Session(connexion, acces, idUtilisateur(connexion));
    }

    private ResultActions appel(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete,
                                String acces, Object corps) throws Exception {
        requete.with(depuisIpClient()).header(HttpHeaders.AUTHORIZATION, "Bearer " + acces);
        if (corps != null) {
            requete.contentType(MediaType.APPLICATION_JSON).content(json(corps));
        }
        return mockMvc.perform(requete);
    }

    private Utilisateur compte(Role role) {
        Utilisateur u = new Utilisateur(emailUnique(), encodeur.encode(MOT_DE_PASSE), "Fatou", "Sow");
        u.setRole(role);
        utilisateurDepot.save(u);
        enroler(u);
        return u;
    }

    // ------------------------------------------------------------------ accès

    @Test
    void seulUnSuperAdministrateurGereLesComptes() throws Exception {
        for (Role role : List.of(Role.SERVICE_CLIENT, Role.GESTIONNAIRE_STOCK, Role.ADMIN)) {
            String acces = jetonAcces(creerEtConnecter(role));
            appel(get("/api/admin/utilisateurs"), acces, null).andExpect(status().isForbidden());
            appel(post("/api/admin/utilisateurs"), acces, Map.of("email", emailUnique(), "prenom", "A", "nom", "B",
                    "role", "ADMIN")).andExpect(status().isForbidden());
        }
        appel(get("/api/admin/utilisateurs"), jetonAcces(creerEtConnecter(Role.CLIENT)), null).andExpect(status().isForbidden());
    }

    @Test
    void laListeNeContientQueLesComptesDeLEquipe() throws Exception {
        Session s = superAdminReauthentifie();
        Utilisateur client = new Utilisateur(emailUnique(), encodeur.encode(MOT_DE_PASSE), "Awa", "Diop");
        utilisateurDepot.save(client);
        String corps = appel(get("/api/admin/utilisateurs"), s.acces(), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(corps).contains("\"role\":\"SUPER_ADMIN\"").doesNotContain(client.getEmail());
    }

    // ------------------------------------------------------------------ invitation

    @Test
    void lInvitationExigeUneReauthentificationPuisCreeUnCompteSansMotDePasse() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.SUPER_ADMIN);
        String email = emailUnique();
        Map<String, String> invitation = Map.of("email", email, "prenom", "Mariama", "nom", "Fall", "role", "SERVICE_CLIENT");
        appel(post("/api/admin/utilisateurs"), jetonAcces(connexion), invitation).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAUTHENTIFICATION_REQUISE"));
        assertThat(utilisateurDepot.trouverParEmail(email)).isEmpty();

        Session s = superAdminReauthentifie();
        appel(post("/api/admin/utilisateurs"), s.acces(), invitation).andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SERVICE_CLIENT")).andExpect(jsonPath("$.invitationEnAttente").value(true))
                .andExpect(jsonPath("$.secondFacteurActif").value(false));

        // Le compte ne se connecte pas tant que l'invitation n'est pas acceptée
        connecter(email, MOT_DE_PASSE).andExpect(status().isUnauthorized());
        connecter(email, "!invitation").andExpect(status().isUnauthorized());

        var recu = mailpit.dernier(email);
        assertThat(recu.sujet()).isEqualTo("Votre accès à l'administration de la boutique");
        assertThat(recu.html()).contains("Service client", "48 heures", "http://localhost:4200/admin/invitation?jeton=");
        assertThat(jdbc.queryForObject("select count(*) from journal_administration where action = 'UTILISATEUR_INVITE' "
                + "and utilisateur_id = ?", Integer.class, s.id())).isEqualTo(1);

        // Mot de passe trop court pour l'équipe : refusé, et le lien reste valable
        mockMvc.perform(post("/api/auth/reinitialiser").with(depuisIpClient()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("jeton", recu.jeton(), "motDePasse", "Court-1234")))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_EQUIPE_TROP_COURT"));
        mockMvc.perform(post("/api/auth/reinitialiser").with(depuisIpClient()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("jeton", recu.jeton(), "motDePasse", "Mot-de-passe-equipe-2026")))).andExpect(status().isOk());

        // Première connexion : enrôlement obligatoire, jamais de session avec le seul mot de passe
        connecter(email, "Mot-de-passe-equipe-2026").andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("ENROLEMENT_REQUIS")).andExpect(jsonPath("$.jetonAcces").doesNotExist());
    }

    @Test
    void uneAdresseDejaUtiliseeOuUnRoleClientSontRefuses() throws Exception {
        Session s = superAdminReauthentifie();
        Utilisateur existant = compte(Role.ADMIN);
        appel(post("/api/admin/utilisateurs"), s.acces(), Map.of("email", existant.getEmail().toUpperCase(), "prenom", "A",
                "nom", "B", "role", "ADMIN")).andExpect(status().isConflict());
        appel(post("/api/admin/utilisateurs"), s.acces(), Map.of("email", emailUnique(), "prenom", "A", "nom", "B",
                "role", "CLIENT")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ROLE_NON_INTERNE"));
    }

    // ------------------------------------------------------------------ rôle, désactivation

    @Test
    void changerLeRoleRevoqueLesSessionsEtSeJournalise() throws Exception {
        Session s = superAdminReauthentifie();
        MvcResult cible = creerEtConnecter(Role.SERVICE_CLIENT);
        long idCible = idUtilisateur(cible);
        assertThat(jetonsActifs(idCible)).isEqualTo(1);

        appel(patch("/api/admin/utilisateurs/" + idCible + "/role"), s.acces(), Map.of("role", "GESTIONNAIRE_STOCK"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("GESTIONNAIRE_STOCK"));
        assertThat(jetonsActifs(idCible)).isZero();
        rafraichir(jetonRafraichissement(cible)).andExpect(status().isUnauthorized());
        // Le jeton d'accès encore valide n'agit plus avec l'ancien rôle
        appel(get("/api/admin/commandes"), jetonAcces(cible), null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALIDE"));

        Map<String, Object> ligne = jdbc.queryForMap("select cast(avant as text) as avant, cast(apres as text) as apres "
                + "from journal_administration where action = 'UTILISATEUR_ROLE_CHANGE' and entite_id = ?", String.valueOf(idCible));
        assertThat((String) ligne.get("avant")).contains("SERVICE_CLIENT");
        assertThat((String) ligne.get("apres")).contains("GESTIONNAIRE_STOCK");
    }

    @Test
    void onNeChangeNiNeDesactiveSonProprePropreCompteNiLeDernierSuperAdministrateur() throws Exception {
        Session s = superAdminReauthentifie();
        appel(patch("/api/admin/utilisateurs/" + s.id() + "/role"), s.acces(), Map.of("role", "ADMIN"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ACTION_SUR_SOI_REFUSEE"));
        appel(post("/api/admin/utilisateurs/" + s.id() + "/desactiver"), s.acces(), null)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ACTION_SUR_SOI_REFUSEE"));

        // Les autres super-administrateurs actifs de la base (autres tests) sont mis de côté : un seul reste, l'autre cible
        List<Long> autres = jdbc.queryForList("select id from utilisateur where role = 'SUPER_ADMIN' and actif and id <> ?",
                Long.class, s.id());
        Utilisateur seconde = compte(Role.SUPER_ADMIN);
        try {
            jdbc.update("update utilisateur set actif = false where role = 'SUPER_ADMIN' and id not in (?, ?)", s.id(), seconde.getId());
            appel(post("/api/admin/utilisateurs/" + seconde.getId() + "/desactiver"), s.acces(), null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
            appel(post("/api/admin/utilisateurs/" + seconde.getId() + "/activer"), s.acces(), null).andExpect(status().isOk());
            // Rôle d'un super-administrateur : autorisé tant qu'il en reste un autre actif (s lui-même)
            appel(patch("/api/admin/utilisateurs/" + seconde.getId() + "/role"), s.acces(), Map.of("role", "ADMIN"))
                    .andExpect(status().isOk());
            // Garde-fou du service (l'API ne peut pas l'atteindre : l'appelant est lui-même un super-administrateur actif) :
            // le dernier super-administrateur actif ne peut être ni désactivé ni rétrogradé
            jdbc.update("update utilisateur set actif = false where role = 'SUPER_ADMIN' and id <> ?", s.id());
            assertThatThrownBy(() -> service.desactiver(-1L, s.id())).isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("dernier super-administrateur");
            assertThatThrownBy(() -> service.changerRole(-1L, s.id(), Role.ADMIN)).isInstanceOf(RegleMetierException.class);
        } finally {
            for (Long id : autres) {
                jdbc.update("update utilisateur set actif = true where id = ?", id);
            }
        }
    }

    @Test
    void uneDesactivationCoupeLaSessionEtLeCompteNePeutPlusSeConnecter() throws Exception {
        Session s = superAdminReauthentifie();
        MvcResult cible = creerEtConnecter(Role.ADMIN);
        long idCible = idUtilisateur(cible);
        String emailCible = corps(cible).get("utilisateur").get("email").asString();

        appel(post("/api/admin/utilisateurs/" + idCible + "/desactiver"), s.acces(), null).andExpect(status().isOk());
        appel(get("/api/admin/commandes"), jetonAcces(cible), null).andExpect(status().isUnauthorized());
        rafraichir(jetonRafraichissement(cible)).andExpect(status().isUnauthorized());
        connecter(emailCible, MOT_DE_PASSE).andExpect(status().isUnauthorized());
    }

    @Test
    void unClientNEstJamaisGereIci() throws Exception {
        Session s = superAdminReauthentifie();
        Utilisateur client = new Utilisateur(emailUnique(), encodeur.encode(MOT_DE_PASSE), "Awa", "Diop");
        utilisateurDepot.save(client);
        appel(patch("/api/admin/utilisateurs/" + client.getId() + "/role"), s.acces(), Map.of("role", "ADMIN"))
                .andExpect(status().isNotFound());
        appel(post("/api/admin/utilisateurs/" + client.getId() + "/desactiver"), s.acces(), null).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ second facteur

    @Test
    void reinitialiserLeSecondFacteurEffaceSecretEtCodesRevoqueLesSessionsEtPrevientLeTitulaire() throws Exception {
        Session s = superAdminReauthentifie();
        MvcResult cible = creerEtConnecter(Role.SERVICE_CLIENT);
        long idCible = idUtilisateur(cible);
        String emailCible = corps(cible).get("utilisateur").get("email").asString();
        jdbc.update("insert into code_secours (utilisateur_id, empreinte) values (?, ?)", idCible, "a".repeat(64));

        appel(post("/api/admin/utilisateurs/" + idCible + "/reinitialiser-second-facteur"), s.acces(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.secondFacteurActif").value(false));

        Utilisateur apres = utilisateurDepot.findById(idCible).orElseThrow();
        assertThat(apres.secondFacteurActif()).isFalse();
        assertThat(apres.getTotpSecretChiffre()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from code_secours where utilisateur_id = ?", Integer.class, idCible)).isZero();
        assertThat(jetonsActifs(idCible)).isZero();
        assertThat(mailpit.dernier(emailCible).sujet()).isEqualTo("Votre double authentification a été réinitialisée");

        // À sa prochaine connexion, le compte doit s'enrôler de nouveau
        connecter(emailCible, MOT_DE_PASSE).andExpect(status().isOk()).andExpect(jsonPath("$.etape").value("ENROLEMENT_REQUIS"));
        assertThat(jdbc.queryForObject("select count(*) from journal_administration where action = 'SECOND_FACTEUR_REINITIALISE' "
                + "and entite_id = ?", Integer.class, String.valueOf(idCible))).isEqualTo(1);
    }

    @Test
    void onNeReinitialisePasLeSecondFacteurQuiNousAOuvertLaSession() throws Exception {
        Session s = superAdminReauthentifie();
        appel(post("/api/admin/utilisateurs/" + s.id() + "/reinitialiser-second-facteur"), s.acces(), null)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ACTION_SUR_SOI_REFUSEE"));
    }

    // ------------------------------------------------------------------ mon compte

    @Test
    void monCompteAvertitTantQuIlNYAQuUnSuperAdministrateur() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.SUPER_ADMIN);
        long moi = idUtilisateur(connexion);
        List<Long> autres = jdbc.queryForList("select id from utilisateur where role = 'SUPER_ADMIN' and actif and id <> ?",
                Long.class, moi);
        try {
            jdbc.update("update utilisateur set actif = false where role = 'SUPER_ADMIN' and id <> ?", moi);
            appel(get("/api/admin/moi"), jetonAcces(connexion), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.avertissements[?(@.code == 'UN_SEUL_SUPER_ADMIN')]").exists())
                    .andExpect(jsonPath("$.secondFacteurActif").value(true));

            Utilisateur seconde = compte(Role.SUPER_ADMIN);
            appel(get("/api/admin/moi"), jetonAcces(connexion), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.avertissements[?(@.code == 'UN_SEUL_SUPER_ADMIN')]").doesNotExist());
            assertThat(seconde.getId()).isNotNull();
        } finally {
            for (Long id : autres) {
                jdbc.update("update utilisateur set actif = true where id = ?", id);
            }
        }
    }

    @Test
    void monCompteAvertitQuandLesCodesDeSecoursSEpuisent() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        long id = idUtilisateur(connexion);
        appel(get("/api/admin/moi"), jetonAcces(connexion), null)
                .andExpect(jsonPath("$.avertissements[?(@.code == 'CODES_SECOURS_BAS')]").exists());
        for (int i = 0; i < 6; i++) {
            jdbc.update("insert into code_secours (utilisateur_id, empreinte) values (?, ?)", id, String.valueOf(i).repeat(64));
        }
        appel(get("/api/admin/moi"), jetonAcces(connexion), null)
                .andExpect(jsonPath("$.avertissements[?(@.code == 'CODES_SECOURS_BAS')]").doesNotExist())
                .andExpect(jsonPath("$.codesSecoursRestants").value(6));
    }

    @Test
    void regenererLesCodesExigeLeMotDePasseEtUnCodeTotp() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        String acces = jetonAcces(connexion);
        long id = idUtilisateur(connexion);
        horloge.avancer(Duration.ofSeconds(30));

        appel(post("/api/admin/moi/codes-secours"), acces, Map.of("motDePasse", "mauvais", "code", codeTotp()))
                .andExpect(status().isUnprocessableContent());
        appel(post("/api/admin/moi/codes-secours"), acces, Map.of("motDePasse", MOT_DE_PASSE, "code", "000000"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CODE_INVALIDE"));
        appel(post("/api/admin/moi/codes-secours"), acces, Map.of("motDePasse", MOT_DE_PASSE, "code", codeTotp()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codesSecours.length()").value(10));
        assertThat(jdbc.queryForObject("select count(*) from code_secours where utilisateur_id = ?", Integer.class, id)).isEqualTo(10);
        // Non confirmés : la prochaine connexion les représente
        assertThat(utilisateurDepot.findById(id).orElseThrow().getCodesSecoursConfirmesLe()).isNull();
        appel(post("/api/admin/moi/codes-secours/confirmer"), acces, null).andExpect(status().isNoContent());
        assertThat(utilisateurDepot.findById(id).orElseThrow().getCodesSecoursConfirmesLe()).isNotNull();
    }
}
