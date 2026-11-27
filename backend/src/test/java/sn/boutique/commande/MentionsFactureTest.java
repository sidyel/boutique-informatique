package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.compte.modele.Role;

/**
 * Mentions légales et régime de TVA des factures, réglés par l'admin. Les réglages du site sont restaurés après
 * chaque test (extension commune).
 */
class MentionsFactureTest extends CommandeTestBase {

    private static final String CORPS = """
            {"raisonSociale":"  Boutique Informatique SARL ","ninea":"0012345 2G2","rccm":"SN-DKR-2026-B-1234",
             "adresseSiege":"Avenue Cheikh Anta Diop, Dakar","regimeTva":"ASSUJETTI","tauxTvaBp":1800,
             "mentionTva":"TVA 18 % incluse"}
            """;

    private ResultActions modifier(String acces, String corps) throws Exception {
        return mockMvc.perform(put("/api/admin/parametres/facturation").header(HttpHeaders.AUTHORIZATION, acces)
                .contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    @Test
    void parDefautLeRegimeEstTvaNonApplicableEtLesMentionsSontAComplete() throws Exception {
        mockMvc.perform(get("/api/admin/parametres/facturation")
                        .header(HttpHeaders.AUTHORIZATION, acces(utilisateur(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.regimeTva").value("NON_APPLICABLE"))
                .andExpect(jsonPath("$.mentionTva").value("TVA non applicable"))
                .andExpect(jsonPath("$.manquantes[0]").value("raison sociale"));
    }

    @Test
    void lAdminRenseigneLesMentionsEtLeRegimeEstEnregistre() throws Exception {
        modifier(acces(utilisateur(Role.ADMIN)), CORPS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.raisonSociale").value("Boutique Informatique SARL"))
                .andExpect(jsonPath("$.regimeTva").value("ASSUJETTI"))
                .andExpect(jsonPath("$.manquantes").isEmpty());

        assertThat(jdbc.sql("select facture_ninea from parametres_site where id = 1").query(String.class).single())
                .isEqualTo("0012345 2G2");
        assertThat(jdbc.sql("select facture_taux_tva_bp from parametres_site where id = 1").query(Integer.class)
                .single()).isEqualTo(1800);
    }

    @Test
    void laMentionTtcDuSiteNeSAfficheQueSiLeVendeurEstAssujetti() throws Exception {
        String admin = acces(utilisateur(Role.ADMIN));
        mockMvc.perform(get("/api/parametres")).andExpect(status().isOk())
                .andExpect(jsonPath("$.afficherTtc").value(false));

        modifier(admin, CORPS).andExpect(status().isOk());
        mockMvc.perform(get("/api/parametres")).andExpect(jsonPath("$.afficherTtc").value(true));

        modifier(admin, CORPS.replace("ASSUJETTI", "NON_APPLICABLE")).andExpect(status().isOk());
        mockMvc.perform(get("/api/parametres")).andExpect(jsonPath("$.afficherTtc").value(false));
    }

    @Test
    void seulsAdminEtSuperAdminPeuventLesModifier() throws Exception {
        modifier(acces(utilisateur(Role.SERVICE_CLIENT)), CORPS).andExpect(status().isForbidden());
        modifier(acces(utilisateur(Role.CLIENT)), CORPS).andExpect(status().isForbidden());
        modifier("", CORPS).andExpect(status().isUnauthorized());
        modifier(acces(utilisateur(Role.SUPER_ADMIN)), CORPS).andExpect(status().isOk());
    }

    @Test
    void lesValeursInvalidesSontRefuseesEnProblemDetail() throws Exception {
        String admin = acces(utilisateur(Role.ADMIN));
        // Régime absent, taux hors bornes, mention vide
        modifier(admin, """
                {"regimeTva":null,"tauxTvaBp":20000,"mentionTva":" "}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs").isNotEmpty());
        modifier(admin, """
                {"regimeTva":"INCONNU","tauxTvaBp":1800,"mentionTva":"x"}
                """).andExpect(status().isBadRequest());
        assertThat(jdbc.sql("select facture_regime_tva from parametres_site where id = 1").query(String.class)
                .single()).isEqualTo("NON_APPLICABLE");
    }
}
