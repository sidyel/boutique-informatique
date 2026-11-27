package sn.boutique.authentification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.modele.Commande;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.support.FabriqueComptes;

/** « Mes commandes » : liste et détail du compte, y compris les commandes invité rattachées ; jamais celles d'un autre. */
class CommandesCompteTest extends AuthentificationTestBase {

    @Autowired
    private CommandeDepot commandeDepot;

    private String commande(String email, Utilisateur proprietaire) {
        Commande modele = FabriqueComptes.commandeRetrait();
        Commande c = new Commande(modele.getNumero(), email, "77 123 45 67", modele.getModeLivraison(), modele.getModePaiement());
        if (proprietaire != null) {
            c.setUtilisateur(proprietaire);
        }
        return commandeDepot.save(c).getNumero();
    }

    private ResultActions lire(String chemin, String acces) throws Exception {
        return mockMvc.perform(get(chemin).with(depuisIpClient()).header(HttpHeaders.AUTHORIZATION, "Bearer " + acces));
    }

    @Test
    void laListeNeMontreQueMesCommandesDontCellesRattacheesDepuisLInvite() throws Exception {
        String email = emailUnique();
        Utilisateur moi = utilisateurDepot.save(new Utilisateur(email, encodeur.encode(MOT_DE_PASSE), "Awa", "Diop"));
        moi.setEmailVerifie(true);
        utilisateurDepot.save(moi);
        String autre = emailUnique();
        Utilisateur lui = utilisateurDepot.save(new Utilisateur(autre, encodeur.encode(MOT_DE_PASSE), "Moussa", "Ndiaye"));

        String invite = commande(email, null);   // passée sans compte, avec mon e-mail
        String miennne = commande(email, moi);
        String sienne = commande(autre, lui);

        // La connexion rattache la commande invité (adresse vérifiée)
        String acces = jetonAcces(connecter(email, MOT_DE_PASSE).andReturn());
        lire("/api/compte/commandes", acces).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.numero=='" + invite + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.numero=='" + miennne + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.numero=='" + sienne + "')]").doesNotExist());
    }

    @Test
    void leDetailEstIntrouvablePourUneCommandeDUnAutreCompte() throws Exception {
        String email = emailUnique();
        Utilisateur moi = utilisateurDepot.save(new Utilisateur(email, encodeur.encode(MOT_DE_PASSE), "Awa", "Diop"));
        String autre = emailUnique();
        Utilisateur lui = utilisateurDepot.save(new Utilisateur(autre, encodeur.encode(MOT_DE_PASSE), "Moussa", "Ndiaye"));
        String mienne = commande(email, moi);
        String sienne = commande(autre, lui);
        String acces = jetonAcces(connecter(email, MOT_DE_PASSE).andReturn());

        lire("/api/compte/commandes/" + mienne, acces).andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(mienne))
                .andExpect(header().string("Cache-Control", "no-store"));
        lire("/api/compte/commandes/" + sienne, acces).andExpect(status().isNotFound());
        lire("/api/compte/commandes/" + sienne + "/facture", acces).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/compte/commandes/" + sienne + "/reprise-paiement").with(depuisIpClient())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + acces).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of()))).andExpect(status().isNotFound());
    }

    @Test
    void sansSessionLesRoutesDuCompteSontRefusees() throws Exception {
        mockMvc.perform(get("/api/compte/commandes").with(depuisIpClient())).andExpect(status().isUnauthorized());
    }
}
