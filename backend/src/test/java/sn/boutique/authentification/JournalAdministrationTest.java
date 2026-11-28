package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.administration.journal.PurgeJournalAdministration;
import sn.boutique.compte.modele.Role;

/** Journal des actions d'administration (ADR 0009 §9) : lignes détaillées et génériques, masquage, immuabilité, purge. */
class JournalAdministrationTest extends AuthentificationTestBase {

    @Autowired
    PurgeJournalAdministration purge;

    private ResultActions appeler(String chemin, String acces) throws Exception {
        return mockMvc.perform(post(ControleurJournalDeTest.PREFIXE + chemin).with(depuisIpClient())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + acces));
    }

    private List<Map<String, Object>> lignes(long utilisateurId) {
        return jdbc.queryForList("select action, entite_type, entite_id, cast(avant as text) as avant, "
                + "cast(apres as text) as apres, generique, methode, chemin, statut_http, adresse_ip, role, utilisateur_email "
                + "from journal_administration where utilisateur_id = ? and action not in ('CONNEXION_EQUIPE', "
                + "'SECOND_FACTEUR_ACTIVE', 'CODE_SECOURS_UTILISE', 'CODE_SECOND_FACTEUR_REFUSE') order by id", utilisateurId);
    }

    @Test
    void uneActionSansLigneDetailleeRecoitUneLigneGenerique() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        appeler("/silencieuse", jetonAcces(connexion)).andExpect(status().isNoContent());

        List<Map<String, Object>> lignes = lignes(idUtilisateur(connexion));
        assertThat(lignes).hasSize(1);
        Map<String, Object> ligne = lignes.get(0);
        assertThat(ligne).containsEntry("action", "REQUETE_ECRITURE").containsEntry("generique", true)
                .containsEntry("methode", "POST").containsEntry("chemin", ControleurJournalDeTest.PREFIXE + "/silencieuse")
                .containsEntry("statut_http", 204).containsEntry("role", "ADMIN");
        assertThat(ligne.get("utilisateur_email")).isEqualTo(corps(connexion).get("utilisateur").get("email").asString());
    }

    @Test
    void uneActionDetailleeNAjoutePasDeLigneGenerique() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.SUPER_ADMIN);
        appeler("/detaillee", jetonAcces(connexion)).andExpect(status().isNoContent());

        List<Map<String, Object>> lignes = lignes(idUtilisateur(connexion));
        assertThat(lignes).hasSize(1);
        assertThat(lignes.get(0)).containsEntry("action", "TEST_DETAILLE").containsEntry("generique", false)
                .containsEntry("entite_type", "Test").containsEntry("entite_id", "42");
    }

    @Test
    void lesDonneesPersonnellesNeSontJamaisEcritesDansLesValeurs() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        appeler("/detaillee", jetonAcces(connexion)).andExpect(status().isNoContent());

        Map<String, Object> ligne = lignes(idUtilisateur(connexion)).get(0);
        String avant = (String) ligne.get("avant");
        String apres = (String) ligne.get("apres");
        assertThat(avant).contains("\"statut\"").contains("[masqué]").doesNotContain("client@exemple.sn");
        assertThat(apres).contains("1500").contains("\"statut\"")
                .doesNotContain("+221771234567").doesNotContain("Diop");
    }

    @Test
    void uneActionRefuseeOuEnEchecNEstPasJournalisee() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        long id = idUtilisateur(connexion);
        // 404 : pas de ligne générique pour un échec ; 5xx avec ligne détaillée annulée (même transaction)
        mockMvc.perform(post(ControleurJournalDeTest.PREFIXE + "/inconnue").with(depuisIpClient())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAcces(connexion))).andExpect(status().isNotFound());
        appeler("/echoue", jetonAcces(connexion)).andExpect(status().is5xxServerError());
        assertThat(lignes(id)).isEmpty();
    }

    @Test
    void uneLectureNEstPasJournalisee() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        mockMvc.perform(get("/api/admin/moi").with(depuisIpClient())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAcces(connexion))).andExpect(status().isOk());
        assertThat(lignes(idUtilisateur(connexion))).isEmpty();
    }

    // ------------------------------------------------------------------ immuabilité et purge

    @Test
    void leJournalEstImmuable() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        appeler("/silencieuse", jetonAcces(connexion)).andExpect(status().isNoContent());
        long id = idUtilisateur(connexion);

        assertThatThrownBy(() -> jdbc.update("update journal_administration set action = 'X' where utilisateur_id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immuable");
        assertThatThrownBy(() -> jdbc.update("delete from journal_administration where utilisateur_id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immuable");
        assertThatThrownBy(() -> jdbc.execute("truncate table journal_administration"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immuable");
        assertThat(lignes(id)).hasSize(1);
    }

    @Test
    void laPurgeNeSupprimeQueLesLignesDeBienPlusDeTroisAns() {
        long vieille = insererLigneAgee(Duration.ofDays(3 * 365 + 40));
        long recente = insererLigneAgee(Duration.ofDays(3 * 365 - 40));
        purge.purger();
        assertThat(jdbc.queryForObject("select count(*) from journal_administration where id = ?", Integer.class, vieille))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from journal_administration where id = ?", Integer.class, recente))
                .isEqualTo(1);
    }

    /** Insère une ligne datée du passé (par rapport à l'heure réelle, que le déclencheur de la base utilise). */
    private long insererLigneAgee(Duration age) {
        Timestamp date = Timestamp.from(horloge.instant().minus(age));
        return jdbc.queryForObject("""
                insert into journal_administration (date_action, utilisateur_email, role, action)
                values (?, 'test@exemple.sn', 'ADMIN', 'TEST_ANCIEN') returning id""", Long.class, date);
    }

    // ------------------------------------------------------------------ lecture

    @Test
    void seulUnSuperAdministrateurLitLeJournal() throws Exception {
        for (Role role : List.of(Role.SERVICE_CLIENT, Role.GESTIONNAIRE_STOCK, Role.ADMIN)) {
            mockMvc.perform(get("/api/admin/journal").header(HttpHeaders.AUTHORIZATION,
                    "Bearer " + jetonAcces(creerEtConnecter(role)))).andExpect(status().isForbidden());
        }
        MvcResult superAdmin = creerEtConnecter(Role.SUPER_ADMIN);
        appeler("/silencieuse", jetonAcces(superAdmin)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/journal").param("action", "REQUETE_ECRITURE").param("auteur",
                        corps(superAdmin).get("utilisateur").get("email").asString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAcces(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.lignes[0].action").value("REQUETE_ECRITURE"))
                .andExpect(jsonPath("$.lignes[0].generique").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void lesConnexionsDeLEquipeSontJournaliseesSansLeCode() throws Exception {
        MvcResult connexion = creerEtConnecter(Role.ADMIN);
        long id = idUtilisateur(connexion);
        List<String> actions = jdbc.queryForList("select action from journal_administration where utilisateur_id = ? order by id",
                String.class, id);
        assertThat(actions).contains("CONNEXION_EQUIPE");
        assertThat(jdbc.queryForObject("select count(*) from journal_administration where utilisateur_id = ? and "
                + "(coalesce(details, '') ~ '[0-9]{6}')", Integer.class, id)).isZero();
    }
}
