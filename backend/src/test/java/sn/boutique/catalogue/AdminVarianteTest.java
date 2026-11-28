package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.catalogue.dto.ModificationVarianteRequete;
import sn.boutique.catalogue.dto.ProduitAdminResumeReponse;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeMedia;
import sn.boutique.catalogue.service.VarianteAdminService;

/** PATCH /api/admin/variantes/{id} : agrégats relus après le trigger (ADR 0004), cache invalidé, rôles. */
class AdminVarianteTest extends CatalogueTestBase {

    @Autowired
    org.springframework.jdbc.core.simple.JdbcClient jdbc;

    @Autowired
    VarianteAdminService varianteAdminService;

    private Categorie categorie;
    private Marque marque;
    private Produit produit;
    private long id8Go;
    private long id16Go;

    @BeforeEach
    void creerLeProduit() {
        categorie = categorie("PC portables admin", null, 0, true);
        marque = marque("Asus");
        String sku8 = unique("VIVO-8");
        String sku16 = unique("VIVO-16");
        produit = produit("Vivobook 15 admin", marque, categorie, p -> {
            p.ajouterVariante(sku8, "8 Go", 389_000, 7);
            p.ajouterVariante(sku16, "16 Go", 449_000, 2);
            p.ajouterMedia(TypeMedia.IMAGE, "https://exemple.sn/vivo.jpg", "Vivobook 15");
        });
        id8Go = idVariante(sku8);
        id16Go = idVariante(sku16);
    }

    // --- ADR 0004 : prixMinimum et stockTotal viennent d'un trigger, le service relit le produit ---------

    @Test
    void leServiceRenvoieLesAgregatsRecalculesParLeTrigger() {
        assertThat(produit.getPrixMinimum()).isEqualTo(389_000);

        ProduitAdminResumeReponse augmente = varianteAdminService.modifier(id8Go,
                ModificationVarianteRequete.de(459_000L, null, false, null));
        assertThat(augmente.prixMinimum()).as("la 16 Go devient la moins chère").isEqualTo(449_000);
        assertThat(augmente.stockTotal()).isEqualTo(9);

        ProduitAdminResumeReponse desactive = varianteAdminService.modifier(id16Go,
                ModificationVarianteRequete.de(null, null, false, false));
        assertThat(desactive.prixMinimum()).isEqualTo(459_000);
        assertThat(desactive.stockTotal()).as("stock des variantes actives seulement").isEqualTo(7);
        assertThat(desactive.variantes()).extracting(ProduitAdminResumeReponse.Variante::actif)
                .containsExactly(false, true);
    }

    @Test
    void laRouteModifieLePrixEtLaFichePubliqueSuitImmediatement() throws Exception {
        String fiche = "/api/produits/" + produit.getSlug();
        String liste = "/api/produits?categorie=" + categorie.getSlug();
        assertThat(corps(lire(fiche)).get("variantes").get(0).get("prix").asLong()).isEqualTo(389_000);
        assertThat(corps(lire(liste)).get("elements").get(0).get("prix").asLong()).isEqualTo(389_000);

        modifier(id8Go, "{\"prix\": 369000, \"prixBarre\": 389000}", "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prixMinimum").value(369_000))
                .andExpect(jsonPath("$.variantes[0].prixBarre").value(389_000));

        // Caches publics invalidés après la validation : plus de prix périmé
        assertThat(corps(lire(fiche)).get("variantes").get(0).get("prix").asLong()).isEqualTo(369_000);
        assertThat(corps(lire(fiche)).get("variantes").get(0).get("reduction").asInt()).isEqualTo(5);
        assertThat(corps(lire(liste)).get("elements").get(0).get("prix").asLong()).isEqualTo(369_000);
    }

    @Test
    void unPrixBarreNullExpliciteLeRetireEtUnPrixBarreTropBasEstRefuse() throws Exception {
        modifier(id16Go, "{\"prixBarre\": 479000}", "SUPER_ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.variantes[1].prixBarre").value(479_000));
        // Champ absent : inchangé
        modifier(id16Go, "{\"prix\": 439000}", "ADMIN").andExpect(jsonPath("$.variantes[1].prixBarre").value(479_000));
        // null explicite : retiré
        modifier(id16Go, "{\"prixBarre\": null}", "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.variantes[1].prixBarre").doesNotExist());

        modifier(id16Go, "{\"prixBarre\": 439000}", "ADMIN").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRIX_BARRE_INVALIDE"));
        assertThat(varianteAdminService.modifier(id16Go, ModificationVarianteRequete.de(null, null, true, null))
                .variantes().get(1).prixBarre()).isNull();
    }

    @Test
    void laDerniereVarianteActiveDUnProduitPublieNePeutPasEtreDesactivee() throws Exception {
        modifier(id8Go, "{\"actif\": false}", "ADMIN").andExpect(status().isOk());
        modifier(id16Go, "{\"actif\": false}", "ADMIN").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DERNIERE_VARIANTE_ACTIVE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}","{\"prixBarreFourni\": true}", "{\"prix\": 0}", "{\"prix\": -5}", "{\"prixBarre\": -1}", "{\"prix\": \"cher\"}",
            "{\"prix\": 2000000000}"})
    void uneRequeteInvalideRenvoie400(String corps) throws Exception {
        modifier(id8Go, corps, "ADMIN").andExpect(status().isBadRequest());
    }

    @Test
    void uneVarianteInconnueRenvoie404() throws Exception {
        modifier(999_999_999L, "{\"prix\": 1000}", "ADMIN").andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GESTIONNAIRE_STOCK", "SERVICE_CLIENT", "CLIENT"})
    void lesAutresRolesSontRefuses(String role) throws Exception {
        modifier(id8Go, "{\"prix\": 1000}", role).andExpect(status().isForbidden());
        assertThat(corps(lire("/api/produits/" + produit.getSlug())).get("variantes").get(0).get("prix").asLong())
                .isEqualTo(389_000);
    }

    @Test
    void sansJetonLaRouteRenvoie401() throws Exception {
        mockMvc.perform(patch("/api/admin/variantes/{id}", id8Go).contentType(MediaType.APPLICATION_JSON)
                .content("{\"prix\": 1000}")).andExpect(status().isUnauthorized());
    }

    private ResultActions modifier(long id, String json, String role) throws Exception {
        return mockMvc.perform(patch("/api/admin/variantes/{id}", id)
                .with(sn.boutique.support.JwtEquipe.pour(jdbc, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private long idVariante(String sku) {
        return enTransaction(em -> em.createQuery("select v.id from VarianteProduit v where v.sku = :sku", Long.class)
                .setParameter("sku", sku).getSingleResult());
    }
}
