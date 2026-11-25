package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import sn.boutique.apresvente.modele.Avis;
import sn.boutique.apresvente.modele.StatutAvis;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.commande.modele.ZoneLivraison;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.support.FabriqueComptes;
import tools.jackson.databind.JsonNode;

/** Avis publiés d'un produit et zones de livraison : lectures publiques de la fiche produit. */
class AvisEtLivraisonPublicsTest extends CatalogueTestBase {

    // --- Avis ---------------------------------------------------------------------------------------

    @Test
    void seulsLesAvisApprouvesSontPubliesDuPlusRecentAuPlusAncienSansDonneePersonnelle() throws Exception {
        Produit produit = produit("Vivobook 15", marque("Asus"), categorie("PC portables", null, 0, true), true);
        Avis ancien = avis(produit, true, "Awa D.", 4, StatutAvis.APPROUVE, true, "Bon portable");
        Avis enAttente = avis(produit, true, "Awa D.", 1, StatutAvis.EN_ATTENTE, true, "Pas encore modéré");
        // Nom saisi en entier : seule l'initiale du nom de famille est publiée
        Avis nomComplet = avis(produit, true, "moussa Diop", 5, StatutAvis.APPROUVE, false, "Très rapide");
        // Comptes supprimés (ADR 0004 §3) : jamais d'achat affirmé s'il n'a pas eu lieu
        Avis supprimeVerifie = avis(produit, false, "Client vérifié", 5, StatutAvis.APPROUVE, true, "Parfait");
        Avis supprimeNonVerifie = avis(produit, false, "Fatou S.", 3, StatutAvis.APPROUVE, false, "Correct");
        enTransaction(em -> {
            Avis charge = em.find(Avis.class, supprimeVerifie.getId());
            charge.repondre("Merci pour votre confiance !", horloge.instant());
            return charge;
        });

        JsonNode page = corps(lire("/api/produits/{slug}/avis", produit.getSlug()).andExpect(status().isOk()));

        assertThat(page.get("totalElements").asLong()).isEqualTo(4);
        assertThat(page.get("taille").asInt()).isEqualTo(5);
        List<Long> ids = new ArrayList<>();
        page.get("elements").forEach(a -> ids.add(a.get("id").asLong()));
        assertThat(ids).containsExactly(supprimeNonVerifie.getId(), supprimeVerifie.getId(), nomComplet.getId(),
                ancien.getId());
        assertThat(ids).doesNotContain(enAttente.getId());

        JsonNode premier = page.get("elements").get(0);
        assertThat(premier.get("auteur").asString()).isEqualTo("Client");
        assertThat(premier.get("achatVerifie").asBoolean()).isFalse();
        assertThat(page.get("elements").get(1).get("auteur").asString()).isEqualTo("Client vérifié");
        assertThat(page.get("elements").get(1).get("reponseBoutique").asString())
                .isEqualTo("Merci pour votre confiance !");
        assertThat(page.get("elements").get(2).get("auteur").asString()).isEqualTo("Moussa D.");
        assertThat(page.get("elements").get(3).get("auteur").asString()).isEqualTo("Awa D.");
        assertThat(page.get("elements").get(3).get("note").asInt()).isEqualTo(4);
        assertThat(page.get("elements").get(3).get("titre").asString()).isEqualTo("Bon portable");

        // Ni e-mail, ni identifiant de compte, ni photos (phase 8), nulle part dans la réponse
        String brut = page.toString();
        assertThat(brut).doesNotContain("@exemple.sn", "utilisateur", "email", "Diop", "photos");
    }

    @Test
    void lesAvisSontPaginesEtLesParametresVerifies() throws Exception {
        Produit produit = produit("Ideapad 3", marque("Lenovo"), categorie("PC portables", null, 0, true), true);
        for (int i = 1; i <= 7; i++) {
            avis(produit, true, "Awa D.", 5, StatutAvis.APPROUVE, true, "Avis " + i);
        }

        lire("/api/produits/{slug}/avis?page=2&taille=5", produit.getSlug()).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.elements.length()").value(2))
                // Les plus anciens en dernière page
                .andExpect(jsonPath("$.elements[1].titre").value("Avis 1"));

        lire("/api/produits/{slug}/avis?taille=21", produit.getSlug()).andExpect(status().isBadRequest());
        lire("/api/produits/{slug}/avis?page=0", produit.getSlug()).andExpect(status().isBadRequest());
        lire("/api/produits/{slug}/avis", "produit-inexistant").andExpect(status().isNotFound());
        Produit retire = produit("Modèle retiré", marque("Lenovo"), categorie("PC portables", null, 0, true), false);
        lire("/api/produits/{slug}/avis", retire.getSlug()).andExpect(status().isNotFound());
    }

    // --- Zones de livraison -------------------------------------------------------------------------

    @Test
    void seulesLesZonesActivesSontPublieesDeLaMoinsChereALaPlusChere() throws Exception {
        String nomBanlieue = unique("Banlieue"), nomCentre = unique("Centre"), nomFermee = unique("Fermée");
        enTransaction(em -> {
            ZoneLivraison banlieue = new ZoneLivraison(nomBanlieue, 3_500, 1);
            banlieue.getVillesQuartiers().addAll(List.of("Pikine", "Guédiawaye"));
            ZoneLivraison centre = new ZoneLivraison(nomCentre, 1_000, 1);
            centre.getVillesQuartiers().add("Plateau");
            ZoneLivraison fermee = new ZoneLivraison(nomFermee, 2_000, 3);
            fermee.setActif(false);
            em.persist(banlieue);
            em.persist(centre);
            em.persist(fermee);
            return null;
        });

        JsonNode zones = corps(lire("/api/livraison/zones").andExpect(status().isOk()));

        List<String> noms = new ArrayList<>();
        zones.forEach(z -> noms.add(z.get("nom").asString()));
        assertThat(noms).contains(nomCentre, nomBanlieue).doesNotContain(nomFermee);
        assertThat(noms.indexOf(nomCentre)).isLessThan(noms.indexOf(nomBanlieue));
        JsonNode banlieue = zones.get(noms.indexOf(nomBanlieue));
        assertThat(banlieue.get("tarif").asLong()).isEqualTo(3_500);
        assertThat(banlieue.get("delaiJours").asInt()).isEqualTo(1);
        assertThat(banlieue.get("villesQuartiers").get(1).asString()).isEqualTo("Guédiawaye");
        assertThat(banlieue.has("id")).isFalse();
    }

    // --- Outils -------------------------------------------------------------------------------------

    /** Un compte par avis (un seul avis par client et par produit) ; sans compte : compte supprimé. */
    private Avis avis(Produit produit, boolean avecCompte, String nomAuteur, int note, StatutAvis statut,
                      boolean achatVerifie, String titre) {
        // Dates de création distinctes : l'ordre « plus récent d'abord » est net
        horloge.avancer(Duration.ofMinutes(1));
        return enTransaction(em -> {
            Utilisateur auteur = avecCompte ? FabriqueComptes.utilisateur(em) : null;
            Avis avis = new Avis(em.getReference(Produit.class, produit.getId()), auteur, nomAuteur, note);
            avis.setStatut(statut);
            avis.setAchatVerifie(achatVerifie);
            avis.setTitre(titre);
            em.persist(avis);
            return avis;
        });
    }
}
