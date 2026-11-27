package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import sn.boutique.compte.modele.Role;

/** Carnet d'adresses : adresse par défaut unique, plafond, propriété (404 pour l'adresse d'un autre). */
class CarnetAdressesTest extends AuthentificationTestBase {

    private static Map<String, Object> adresse(String libelle, boolean parDefaut) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("libelle", libelle);
        m.put("nomComplet", "Awa Diop");
        m.put("telephone", "77 123 45 67");
        m.put("ville", "Dakar");
        m.put("quartier", "Mermoz");
        m.put("pointDeRepere", "En face de la pharmacie");
        m.put("parDefaut", parDefaut);
        return m;
    }

    private ResultActions appeler(MockHttpServletRequestBuilder requete, String acces, Object corps) throws Exception {
        requete.with(depuisIpClient()).header(HttpHeaders.AUTHORIZATION, "Bearer " + acces);
        if (corps != null) {
            requete.contentType(MediaType.APPLICATION_JSON).content(json(corps));
        }
        return mockMvc.perform(requete);
    }

    private long creer(String acces, String libelle, boolean parDefaut) throws Exception {
        MvcResult r = appeler(post("/api/compte/adresses"), acces, adresse(libelle, parDefaut))
                .andExpect(status().isCreated()).andReturn();
        return corps(r).get("id").asLong();
    }

    @Test
    void laPremiereAdresseEstParDefautEtUneSeuleLEstEnsuite() throws Exception {
        String acces = jetonAcces(creerEtConnecter(Role.CLIENT));
        long maison = creer(acces, "Maison", false);
        long bureau = creer(acces, "Bureau", true);

        appeler(get("/api/compte/adresses"), acces, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(bureau))
                .andExpect(jsonPath("$[0].parDefaut").value(true))
                .andExpect(jsonPath("$[1].id").value(maison))
                .andExpect(jsonPath("$[1].parDefaut").value(false))
                .andExpect(jsonPath("$[1].telephone").value("+221771234567"));

        appeler(put("/api/compte/adresses/" + maison + "/par-defaut"), acces, null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from adresse where par_defaut and id in (?, ?)", Integer.class,
                maison, bureau)).isEqualTo(1);
    }

    @Test
    void supprimerLAdresseParDefautPromeutLaPlusRecente() throws Exception {
        String acces = jetonAcces(creerEtConnecter(Role.CLIENT));
        long premiere = creer(acces, "Maison", true);
        long deuxieme = creer(acces, "Bureau", false);

        appeler(delete("/api/compte/adresses/" + premiere), acces, null).andExpect(status().isNoContent());
        appeler(get("/api/compte/adresses"), acces, null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(deuxieme))
                .andExpect(jsonPath("$[0].parDefaut").value(true));
    }

    @Test
    void lAdresseDUnAutreCompteEstIntrouvableEtLeCarnetEstPlafonne() throws Exception {
        String acces = jetonAcces(creerEtConnecter(Role.CLIENT));
        String autre = jetonAcces(creerEtConnecter(Role.CLIENT));
        long mienne = creer(acces, "Maison", true);

        appeler(put("/api/compte/adresses/" + mienne), autre, adresse("Volée", false)).andExpect(status().isNotFound());
        appeler(delete("/api/compte/adresses/" + mienne), autre, null).andExpect(status().isNotFound());
        appeler(put("/api/compte/adresses/" + mienne + "/par-defaut"), autre, null).andExpect(status().isNotFound());

        for (int i = 2; i <= 10; i++) {
            creer(acces, "Adresse " + i, false);
        }
        appeler(post("/api/compte/adresses"), acces, adresse("Onzième", false))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TROP_D_ADRESSES"));
    }

    @Test
    void lesChampsObligatoiresEtLeTelephoneSontValides() throws Exception {
        String acces = jetonAcces(creerEtConnecter(Role.CLIENT));
        Map<String, Object> sansQuartier = adresse("Maison", false);
        sansQuartier.put("quartier", " ");
        appeler(post("/api/compte/adresses"), acces, sansQuartier).andExpect(status().isBadRequest());
        Map<String, Object> mauvaisTel = adresse("Maison", false);
        mauvaisTel.put("telephone", "12345");
        appeler(post("/api/compte/adresses"), acces, mauvaisTel).andExpect(status().isBadRequest());
    }
}
