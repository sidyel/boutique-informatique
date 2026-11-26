package sn.boutique.panier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.function.Consumer;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.TestcontainersConfiguration;
import sn.boutique.authentification.service.JetonAccesService;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.modele.ZoneLivraison;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.marketing.modele.CodePromo;
import sn.boutique.marketing.modele.TypeCodePromo;
import sn.boutique.noyau.config.ProprietesBoutique;
import sn.boutique.panier.service.PurgePaniersInvites;
import sn.boutique.support.FabriqueCatalogue;
import sn.boutique.support.FabriqueComptes;
import sn.boutique.support.HorlogeReglable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Panier de bout en bout (MockMvc, vraie chaîne de sécurité, PostgreSQL) : invité par cookie, plafonds de quantité,
 * prix recalculés et changements de prix, codes promo, fusion à la connexion, CSRF, purge.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, HorlogeReglable.Configuration.class})
class PanierTest {

    private static final String COOKIE = "panier_invite";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    HorlogeReglable horloge;
    @Autowired
    EntityManager em;
    @Autowired
    TransactionTemplate transaction;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    JetonAccesService jetons;
    @Autowired
    ProprietesBoutique proprietes;
    @Autowired
    PurgePaniersInvites purge;

    private Categorie categorie;
    private Marque marque;

    @BeforeEach
    void preparer() {
        horloge.reinitialiser();
        categorie = enTransaction(em -> FabriqueCatalogue.categorie(em));
        marque = enTransaction(em -> FabriqueCatalogue.marque(em));
    }

    // ------------------------------------------------------------------ invité

    @Test
    void unVisiteurQuiLitSansRienAjouterNObtientNiPanierNiCookie() throws Exception {
        mockMvc.perform(get("/api/panier"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.lignes.length()").value(0))
                .andExpect(jsonPath("$.commandable").value(false));
    }

    @Test
    void lePremierAjoutPoseUnCookieHttpOnlyEtLaBaseNeGardeQueSonEmpreinte() throws Exception {
        VarianteProduit variante = variante(450_000, 10, null);

        MockHttpServletResponse reponse = modifier(post("/api/panier/lignes"), null,
                "{\"varianteId\":%d,\"quantite\":2}".formatted(variante.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.nombreArticles").value(2))
                .andExpect(jsonPath("$.sousTotal").value(900_000))
                .andExpect(jsonPath("$.total").value(900_000))
                .andExpect(jsonPath("$.fraisLivraison").doesNotExist())
                .andExpect(jsonPath("$.commandable").value(true))
                .andExpect(jsonPath("$.lignes[0].sku").value(variante.getSku()))
                .andExpect(jsonPath("$.lignes[0].prixUnitaire").value(450_000))
                .andExpect(jsonPath("$.lignes[0].quantiteMax").value(5))
                .andReturn().getResponse();

        String setCookie = reponse.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(COOKIE + "=").contains("HttpOnly", "SameSite=Lax", "Path=/api", "Max-Age=2592000");
        String jeton = reponse.getCookie(COOKIE).getValue();
        assertThat(jeton).hasSize(43);
        assertThat(jdbc.sql("select count(*) from panier where jeton_invite = :j").param("j", jeton)
                .query(Long.class).single()).isZero();

        // Relu avec le cookie : même panier, aucun nouveau cookie
        mockMvc.perform(get("/api/panier").cookie(new Cookie(COOKIE, jeton)))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.nombreArticles").value(2));
    }

    @Test
    void unCookieInconnuOuMalFormeEstEfface() throws Exception {
        for (String jeton : new String[] {"a".repeat(43), "pas-un-jeton"}) {
            String setCookie = mockMvc.perform(get("/api/panier").cookie(new Cookie(COOKIE, jeton)))
                    .andExpect(jsonPath("$.lignes.length()").value(0))
                    .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).startsWith(COOKIE + "=;").contains("Max-Age=0");
        }
    }

    @Test
    void lePrixEnvoyeParLeNavigateurEstIgnore() throws Exception {
        VarianteProduit variante = variante(300_000, 10, null);
        modifier(post("/api/panier/lignes"), null,
                "{\"varianteId\":%d,\"quantite\":1,\"prix\":1}".formatted(variante.getId()))
                .andExpect(jsonPath("$.lignes[0].prixUnitaire").value(300_000))
                .andExpect(jsonPath("$.total").value(300_000));
    }

    // ------------------------------------------------------------------ quantités

    @Test
    void laQuantiteEstPlafonneeParLeStockPuisRefusee() throws Exception {
        VarianteProduit variante = variante(100_000, 3, null);
        String jeton = ajouter(null, variante, 5)
                .andExpect(jsonPath("$.lignes[0].quantite").value(3))
                .andExpect(jsonPath("$.lignes[0].quantiteMax").value(3))
                .andExpect(jsonPath("$.avertissements[0]").value(org.hamcrest.Matchers.containsString("stock disponible")))
                .andReturn().getResponse().getCookie(COOKIE).getValue();

        ajouter(jeton, variante, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_INSUFFISANT"))
                .andExpect(jsonPath("$.quantiteMax").value(3));
    }

    @Test
    void laLimiteParLigneVientDeLaVarianteSinonDuSite() throws Exception {
        VarianteProduit limitee = variante(900_000, 50, 2);
        String jeton = ajouter(null, limitee, 3)
                .andExpect(jsonPath("$.lignes[0].quantite").value(2))
                .andExpect(jsonPath("$.lignes[0].quantiteMax").value(2))
                .andExpect(jsonPath("$.avertissements[0]").value(org.hamcrest.Matchers.containsString("maximum par commande")))
                .andReturn().getResponse().getCookie(COOKIE).getValue();
        ajouter(jeton, limitee, 1)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("QUANTITE_MAXIMALE"));

        // Sans exception sur la variante : 5 (ParametresSite.quantiteMaxParLigne) ; le stock (50) n'est pas révélé
        VarianteProduit normale = variante(10_000, 50, null);
        JsonNode panier = corps(ajouter(jeton, normale, 9));
        JsonNode ligne = ligne(panier, normale);
        assertThat(ligne.path("quantite").asInt()).isEqualTo(5);
        assertThat(ligne.path("quantiteMax").asInt()).isEqualTo(5);

        // Modifier la quantité : même plafond
        modifier(patch("/api/panier/lignes/{id}", ligne.path("id").asLong()), jeton, "{\"quantite\":99}")
                .andExpect(status().isOk());
        assertThat(ligne(corps(lirePanier(jeton, null)), normale).path("quantite").asInt()).isEqualTo(5);
        modifier(patch("/api/panier/lignes/{id}", ligne.path("id").asLong()), jeton, "{\"quantite\":0}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void uneLigneDevenueIndisponibleNeCompteplusEtBloqueLaCommande() throws Exception {
        VarianteProduit disponible = variante(200_000, 5, null);
        VarianteProduit bientotMasquee = variante(50_000, 5, null);
        String jeton = cookie(ajouter(null, disponible, 1));
        ajouter(jeton, bientotMasquee, 1);
        enTransaction(em -> {
            em.find(Produit.class, bientotMasquee.getProduit().getId()).setActif(false);
            return null;
        });

        JsonNode panier = corps(lirePanier(jeton, null));
        assertThat(ligne(panier, bientotMasquee).path("probleme").asString()).isEqualTo("INDISPONIBLE");
        assertThat(panier.path("sousTotal").asLong()).isEqualTo(200_000);
        assertThat(panier.path("nombreArticles").asInt()).isEqualTo(2);
        assertThat(panier.path("commandable").asBoolean()).isFalse();

        // Une variante masquée ne peut pas être ajoutée
        ajouter(jeton, bientotMasquee, 1).andExpect(status().isNotFound());

        // Retirer la ligne rend le panier commandable
        modifier(delete("/api/panier/lignes/{id}", ligne(panier, bientotMasquee).path("id").asLong()), jeton, null)
                .andExpect(jsonPath("$.commandable").value(true))
                .andExpect(jsonPath("$.lignes.length()").value(1));
    }

    @Test
    void uneLigneDUnAutrePanierEstIntrouvable() throws Exception {
        VarianteProduit variante = variante(10_000, 5, null);
        String jetonA = cookie(ajouter(null, variante, 1));
        String jetonB = cookie(ajouter(null, variante, 1));
        long ligneDeA = corps(lirePanier(jetonA, null)).path("lignes").get(0).path("id").asLong();

        modifier(delete("/api/panier/lignes/{id}", ligneDeA), jetonB, null).andExpect(status().isNotFound());
        modifier(patch("/api/panier/lignes/{id}", ligneDeA), jetonB, "{\"quantite\":2}")
                .andExpect(status().isNotFound());
        assertThat(corps(lirePanier(jetonA, null)).path("lignes").size()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ changement de prix

    @Test
    void unChangementDePrixEstSignaleEtDoitEtreAccepte() throws Exception {
        VarianteProduit variante = variante(450_000, 5, null);
        String jeton = cookie(ajouter(null, variante, 2));
        changerPrix(variante, 475_000);

        lirePanier(jeton, null)
                .andExpect(jsonPath("$.lignes[0].prixUnitaire").value(475_000))
                .andExpect(jsonPath("$.lignes[0].prixPrecedent").value(450_000))
                .andExpect(jsonPath("$.sousTotal").value(950_000))
                .andExpect(jsonPath("$.prixModifies").value(true))
                .andExpect(jsonPath("$.commandable").value(false));

        modifier(post("/api/panier/accepter-prix"), jeton, null)
                .andExpect(jsonPath("$.lignes[0].prixPrecedent").doesNotExist())
                .andExpect(jsonPath("$.prixModifies").value(false))
                .andExpect(jsonPath("$.commandable").value(true));

        // Une baisse est aussi signalée (le client voit ce qu'il paiera)
        changerPrix(variante, 430_000);
        lirePanier(jeton, null).andExpect(jsonPath("$.lignes[0].prixPrecedent").value(475_000));
    }

    // ------------------------------------------------------------------ codes promo et livraison

    @Test
    void codesPromoRefusesSansEtreEnregistres() throws Exception {
        VarianteProduit variante = variante(100_000, 5, null);
        String jeton = cookie(ajouter(null, variante, 1));
        String expire = code(TypeCodePromo.POURCENTAGE, 10, c -> c.setDateFin(horloge.instant().minus(Duration.ofDays(1))));
        String futur = code(TypeCodePromo.POURCENTAGE, 10, c -> c.setDateDebut(horloge.instant().plus(Duration.ofDays(1))));
        String epuise = code(TypeCodePromo.MONTANT_FIXE, 5_000, c -> c.setUtilisationsMax(3));
        jdbc.sql("update code_promo set nombre_utilisations = 3 where code = :c").param("c", epuise).update();
        String inactif = code(TypeCodePromo.POURCENTAGE, 10, c -> c.setActif(false));

        appliquer(jeton, "INEXISTANT").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_PROMO_INCONNU"));
        appliquer(jeton, "pas valable !").andExpect(jsonPath("$.code").value("CODE_PROMO_INCONNU"));
        appliquer(jeton, inactif).andExpect(jsonPath("$.code").value("CODE_PROMO_INCONNU"));
        appliquer(jeton, expire.toLowerCase()).andExpect(jsonPath("$.code").value("CODE_PROMO_EXPIRE"))
                .andExpect(jsonPath("$.detail").value("Ce code promo a expiré."));
        appliquer(jeton, futur).andExpect(jsonPath("$.code").value("CODE_PROMO_EXPIRE"))
                .andExpect(jsonPath("$.detail").value("Ce code promo n'est pas encore valable."));
        appliquer(jeton, epuise).andExpect(jsonPath("$.code").value("CODE_PROMO_EPUISE"));

        lirePanier(jeton, null).andExpect(jsonPath("$.codePromo").doesNotExist());
    }

    @Test
    void pourcentageMinimumEtCategoriesCiblees() throws Exception {
        Categorie ordinateurs = enTransaction(em -> FabriqueCatalogue.categorie(em));
        Categorie portables = enTransaction(em -> {
            Categorie sousCategorie = FabriqueCatalogue.categorie(em);
            sousCategorie.setParent(em.getReference(Categorie.class, ordinateurs.getId()));
            return sousCategorie;
        });
        VarianteProduit portable = variante(portables, 400_000, 5, null);
        VarianteProduit souris = variante(15_000, 5, null);
        String code = code(TypeCodePromo.POURCENTAGE, 7, c -> {
            c.setMontantMinimum(500_000);
            c.getCategoriesCiblees().add(em.getReference(Categorie.class, ordinateurs.getId()));
        });

        String jeton = cookie(ajouter(null, portable, 1));
        // Minimum non atteint : code gardé, signalé, sans remise
        appliquer(jeton, code).andExpect(status().isOk())
                .andExpect(jsonPath("$.codePromo.valide").value(false))
                .andExpect(jsonPath("$.codePromo.erreur").value("CODE_PROMO_MINIMUM"))
                .andExpect(jsonPath("$.codePromo.message").value("Ce code s'applique à partir de 500 000 FCFA d'achat."))
                .andExpect(jsonPath("$.remise").value(0));

        // 2 portables (sous-catégorie ciblée) + 1 souris (hors cible) : 7 % de 800 000 seulement
        ajouter(jeton, portable, 1);
        ajouter(jeton, souris, 1)
                .andExpect(jsonPath("$.sousTotal").value(815_000))
                .andExpect(jsonPath("$.codePromo.valide").value(true))
                .andExpect(jsonPath("$.remise").value(56_000))
                .andExpect(jsonPath("$.total").value(759_000));

        modifier(delete("/api/panier/code-promo"), jeton, null)
                .andExpect(jsonPath("$.codePromo").doesNotExist())
                .andExpect(jsonPath("$.remise").value(0));
    }

    @Test
    void estimationDeLaLivraisonEtLivraisonOfferte() throws Exception {
        long zone = enTransaction(em -> {
            ZoneLivraison z = new ZoneLivraison(FabriqueCatalogue.unique("Dakar Plateau"), 2_000, 1);
            em.persist(z);
            return z.getId();
        });
        VarianteProduit variante = variante(30_000, 5, null);
        String jeton = cookie(ajouter(null, variante, 1));

        lirePanier(jeton, zone)
                .andExpect(jsonPath("$.fraisLivraison").value(2_000))
                .andExpect(jsonPath("$.total").value(32_000));
        lirePanier(jeton, 999_999L).andExpect(jsonPath("$.fraisLivraison").doesNotExist());

        String offerte = code(TypeCodePromo.LIVRAISON_OFFERTE, 0, c -> { });
        modifier(put("/api/panier/code-promo").param("zone", String.valueOf(zone)), jeton,
                "{\"code\":\"%s\"}".formatted(offerte))
                .andExpect(jsonPath("$.livraisonOfferte").value(true))
                .andExpect(jsonPath("$.fraisLivraison").value(0))
                .andExpect(jsonPath("$.total").value(30_000));
    }

    @Test
    void montantFixePlafonneAuSousTotalEtCodeDevenuExpireSignale() throws Exception {
        VarianteProduit variante = variante(3_000, 5, null);
        String jeton = cookie(ajouter(null, variante, 1));
        String code = code(TypeCodePromo.MONTANT_FIXE, 5_000,
                c -> c.setDateFin(horloge.instant().plus(Duration.ofHours(1))));

        appliquer(jeton, code).andExpect(jsonPath("$.remise").value(3_000)).andExpect(jsonPath("$.total").value(0));

        horloge.avancer(Duration.ofHours(2));
        lirePanier(jeton, null)
                .andExpect(jsonPath("$.codePromo.valide").value(false))
                .andExpect(jsonPath("$.codePromo.erreur").value("CODE_PROMO_EXPIRE"))
                .andExpect(jsonPath("$.remise").value(0))
                .andExpect(jsonPath("$.total").value(3_000));
    }

    // ------------------------------------------------------------------ fusion

    @Test
    void aLaConnexionLePanierInviteEstFusionnePlafonneEtSonCookieEfface() throws Exception {
        Utilisateur client = enTransaction(em -> FabriqueComptes.utilisateur(em));
        String acces = "Bearer " + jetons.emettre(client).valeur();
        VarianteProduit commune = variante(100_000, 4, null);
        VarianteProduit inviteSeule = variante(20_000, 10, null);
        VarianteProduit epuisee = variante(5_000, 2, null);

        // Panier du compte : 3 × commune
        modifier(post("/api/panier/lignes"), null, "{\"varianteId\":%d,\"quantite\":3}".formatted(commune.getId()),
                acces).andExpect(status().isOk()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        // Panier invité : 2 × commune, 1 × inviteSeule (au prix vu de 20 000), 2 × epuisee ; code promo
        String jeton = cookie(ajouter(null, commune, 2));
        ajouter(jeton, inviteSeule, 1);
        ajouter(jeton, epuisee, 2);
        String code = code(TypeCodePromo.POURCENTAGE, 5, c -> { });
        appliquer(jeton, code);
        changerPrix(inviteSeule, 22_000);
        enTransaction(em -> {
            em.find(VarianteProduit.class, epuisee.getId()).setStock(0);
            return null;
        });

        ResultActions fusion = mockMvc.perform(get("/api/panier").cookie(new Cookie(COOKIE, jeton))
                .header(HttpHeaders.AUTHORIZATION, acces));
        assertThat(fusion.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(COOKIE + "=;").contains("Max-Age=0");
        JsonNode panier = corps(fusion);
        assertThat(ligne(panier, commune).path("quantite").asInt()).isEqualTo(4); // 3 + 2, plafonné au stock
        assertThat(ligne(panier, inviteSeule).path("prixPrecedent").asLong()).isEqualTo(20_000);
        assertThat(panier.path("lignes").size()).isEqualTo(2);
        assertThat(panier.path("codePromo").path("code").asString()).isEqualTo(code);
        // Le code de l'invité est repris et s'applique encore : remise de 5 % sur les lignes fusionnées
        assertThat(panier.path("codePromo").path("valide").asBoolean()).isTrue();
        assertThat(panier.path("remise").asLong()).isEqualTo(panier.path("sousTotal").asLong() * 5 / 100);
        assertThat(panier.path("avertissements").toString()).contains("stock disponible", "n'est plus disponible");

        // Le panier invité n'existe plus ; le cookie ne sert plus à rien
        mockMvc.perform(get("/api/panier").cookie(new Cookie(COOKIE, jeton)))
                .andExpect(jsonPath("$.lignes.length()").value(0));
        // Le compte retrouve son panier sans cookie
        mockMvc.perform(get("/api/panier").header(HttpHeaders.AUTHORIZATION, acces))
                .andExpect(jsonPath("$.nombreArticles").value(5));
    }

    // ------------------------------------------------------------------ CSRF et limite

    @Test
    void lesModificationsSansOriginDuSiteSontRefusees() throws Exception {
        VarianteProduit variante = variante(10_000, 5, null);
        String jeton = cookie(ajouter(null, variante, 1));
        String corps = "{\"varianteId\":%d,\"quantite\":1}".formatted(variante.getId());

        mockMvc.perform(post("/api/panier/lignes").cookie(new Cookie(COOKIE, jeton))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGINE_REFUSEE"));
        mockMvc.perform(delete("/api/panier/code-promo").cookie(new Cookie(COOKIE, jeton))
                        .header(HttpHeaders.ORIGIN, "https://site-pirate.com"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/panier/code-promo").cookie(new Cookie(COOKIE, jeton))
                        .header(HttpHeaders.ORIGIN, "https://site-pirate.com")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"BIENVENUE\"}"))
                .andExpect(status().isForbidden());
        assertThat(corps(lirePanier(jeton, null)).path("nombreArticles").asInt()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ purge

    @Test
    void laPurgeSupprimeLesPaniersInvitesAbandonnesSeulement() throws Exception {
        VarianteProduit variante = variante(10_000, 5, null);
        String ancien = cookie(ajouter(null, variante, 1));
        Utilisateur client = enTransaction(em -> FabriqueComptes.utilisateur(em));
        modifier(post("/api/panier/lignes"), null, "{\"varianteId\":%d,\"quantite\":1}".formatted(variante.getId()),
                "Bearer " + jetons.emettre(client).valeur());

        horloge.avancer(Duration.ofDays(31));
        String recent = cookie(ajouter(null, variante, 1));
        purge.purger();

        assertThat(corps(lirePanier(ancien, null)).path("lignes").size()).isZero();
        assertThat(corps(lirePanier(recent, null)).path("lignes").size()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from panier where utilisateur_id = :id").param("id", client.getId())
                .query(Long.class).single()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ outils

    private <T> T enTransaction(java.util.function.Function<EntityManager, T> action) {
        return transaction.execute(statut -> action.apply(em));
    }

    private VarianteProduit variante(long prix, int stock, Integer quantiteMax) {
        return variante(categorie, prix, stock, quantiteMax);
    }

    private VarianteProduit variante(Categorie rayon, long prix, int stock, Integer quantiteMax) {
        horloge.avancer(Duration.ofSeconds(1));
        return enTransaction(em -> {
            Produit produit = new Produit("Produit " + FabriqueCatalogue.unique("p"), FabriqueCatalogue.unique("produit"),
                    em.getReference(Marque.class, marque.getId()), em.getReference(Categorie.class, rayon.getId()));
            VarianteProduit variante = produit.ajouterVariante(FabriqueCatalogue.unique("SKU").toUpperCase(),
                    "Standard", prix, stock);
            variante.setQuantiteMax(quantiteMax);
            em.persist(produit);
            em.flush();
            return variante;
        });
    }

    private void changerPrix(VarianteProduit variante, long prix) {
        enTransaction(em -> {
            em.find(VarianteProduit.class, variante.getId()).setPrix(prix);
            return null;
        });
    }

    private String code(TypeCodePromo type, long valeur, Consumer<CodePromo> configuration) {
        return enTransaction(em -> {
            CodePromo code = new CodePromo(FabriqueCatalogue.unique("PROMO").replace('-', '_'), type, valeur);
            configuration.accept(code);
            em.persist(code);
            return code.getCode();
        });
    }

    private String origine() {
        return proprietes.urlSiteSansBarre();
    }

    private ResultActions modifier(MockHttpServletRequestBuilder requete, String jeton, String corps) throws Exception {
        return modifier(requete, jeton, corps, null);
    }

    private ResultActions modifier(MockHttpServletRequestBuilder requete, String jeton, String corps, String acces)
            throws Exception {
        requete.header(HttpHeaders.ORIGIN, origine());
        if (jeton != null) {
            requete.cookie(new Cookie(COOKIE, jeton));
        }
        if (acces != null) {
            requete.header(HttpHeaders.AUTHORIZATION, acces);
        }
        if (corps != null) {
            requete.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return mockMvc.perform(requete);
    }

    private ResultActions ajouter(String jeton, VarianteProduit variante, int quantite) throws Exception {
        return modifier(post("/api/panier/lignes"), jeton,
                "{\"varianteId\":%d,\"quantite\":%d}".formatted(variante.getId(), quantite));
    }

    private ResultActions appliquer(String jeton, String code) throws Exception {
        return modifier(put("/api/panier/code-promo"), jeton, "{\"code\":\"%s\"}".formatted(code));
    }

    private ResultActions lirePanier(String jeton, Long zone) throws Exception {
        MockHttpServletRequestBuilder requete = get("/api/panier").cookie(new Cookie(COOKIE, jeton));
        if (zone != null) {
            requete.param("zone", String.valueOf(zone));
        }
        return mockMvc.perform(requete);
    }

    private static String cookie(ResultActions resultat) {
        return resultat.andReturn().getResponse().getCookie(COOKIE).getValue();
    }

    private JsonNode corps(ResultActions resultat) throws Exception {
        return jsonMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }

    private static JsonNode ligne(JsonNode panier, VarianteProduit variante) {
        for (JsonNode ligne : panier.path("lignes")) {
            if (ligne.path("varianteId").asLong() == variante.getId()) {
                return ligne;
            }
        }
        throw new AssertionError("Ligne absente pour " + variante.getSku());
    }
}
