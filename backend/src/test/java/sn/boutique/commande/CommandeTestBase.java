package sn.boutique.commande;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
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
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.marketing.modele.CodePromo;
import sn.boutique.marketing.modele.TypeCodePromo;
import sn.boutique.noyau.config.ProprietesBoutique;
import sn.boutique.support.FabriqueCatalogue;
import sn.boutique.support.FabriqueComptes;
import sn.boutique.support.FauxFournisseurPaiement;
import sn.boutique.support.FauxNotificationClient;
import sn.boutique.support.HorlogeReglable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Socle des tests de commande : vraie chaîne de sécurité (MockMvc), PostgreSQL et Redis réels, prestataire de
 * paiement et notifications factices. Chaque test repart de réglages du site par défaut.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, HorlogeReglable.Configuration.class})
abstract class CommandeTestBase {

    static final String COOKIE = "panier_invite";

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
    FauxFournisseurPaiement fournisseur;
    @Autowired
    FauxNotificationClient notifications;

    Categorie categorie;
    Marque marque;
    long zoneId;

    @BeforeEach
    void preparerCommande() {
        horloge.reinitialiser();
        fournisseur.reinitialiser();
        notifications.reinitialiser();
        jdbc.sql("""
                update parametres_site set paiement_livraison_actif = true, paiement_livraison_montant_max = 500000,
                       paiement_livraison_max_non_confirmees = 2, paiement_livraison_delai_annulation_heures = 24,
                       delai_paiement_mobile_minutes = 30, boutique_physique = false
                 where id = 1
                """).update();
        // Les commandes en attente d'un autre test ne doivent ni être balayées par l'expiration de celui-ci, ni
        // encombrer la liste « à confirmer » du service client
        jdbc.sql("update commande set statut = 'ANNULEE', expire_le = null "
                + "where statut in ('EN_ATTENTE_PAIEMENT', 'EN_ATTENTE_CONFIRMATION')").update();
        categorie = enTransaction(e -> FabriqueCatalogue.categorie(e));
        marque = enTransaction(e -> FabriqueCatalogue.marque(e));
        zoneId = enTransaction(e -> {
            ZoneLivraison zone = new ZoneLivraison(FabriqueCatalogue.unique("Dakar Plateau"), 2_000, 1);
            e.persist(zone);
            return zone.getId();
        });
    }

    // ------------------------------------------------------------------ données

    <T> T enTransaction(Function<EntityManager, T> action) {
        return transaction.execute(statut -> action.apply(em));
    }

    VarianteProduit variante(long prix, int stock) {
        horloge.avancer(Duration.ofSeconds(1));
        return enTransaction(e -> {
            Produit produit = new Produit("Produit " + FabriqueCatalogue.unique("p"),
                    FabriqueCatalogue.unique("produit"), e.getReference(Marque.class, marque.getId()),
                    e.getReference(Categorie.class, categorie.getId()));
            VarianteProduit variante = produit.ajouterVariante(FabriqueCatalogue.unique("SKU").toUpperCase(),
                    "Standard", prix, stock);
            e.persist(produit);
            e.flush();
            return variante;
        });
    }

    String code(TypeCodePromo type, long valeur, Consumer<CodePromo> configuration) {
        return enTransaction(e -> {
            CodePromo code = new CodePromo(FabriqueCatalogue.unique("PROMO").replace('-', '_'), type, valeur);
            configuration.accept(code);
            e.persist(code);
            return code.getCode();
        });
    }

    int stock(VarianteProduit variante) {
        return jdbc.sql("select stock from variante_produit where id = :id").param("id", variante.getId())
                .query(Integer.class).single();
    }

    int stockTotalProduit(VarianteProduit variante) {
        return jdbc.sql("select stock_total from produit where id = :id")
                .param("id", variante.getProduit().getId()).query(Integer.class).single();
    }

    long compter(String sql, Object... parametres) {
        JdbcClient.StatementSpec requete = jdbc.sql(sql);
        for (int i = 0; i < parametres.length; i++) {
            requete = requete.param(parametres[i]);
        }
        return requete.query(Long.class).single();
    }

    Utilisateur utilisateur(Role role) {
        return enTransaction(e -> {
            Utilisateur utilisateur = FabriqueComptes.utilisateur(e);
            utilisateur.setRole(role);
            return utilisateur;
        });
    }

    /**
     * Jeton d'accès d'un compte, comme après une connexion : avec une session et, pour l'équipe, une fenêtre de
     * réauthentification déjà ouverte (le code TOTP est testé à part, dans {@code ReauthentificationTest}).
     */
    String acces(Utilisateur utilisateur) {
        java.util.UUID famille = java.util.UUID.randomUUID();
        if (utilisateur.getRole().estInterne()) {
            jdbc.sql("insert into reauthentification (famille, utilisateur_id, valide_jusqu) "
                            + "values (:famille, :utilisateur, timestamptz '2100-01-01 00:00:00+00')")
                    .param("famille", famille).param("utilisateur", utilisateur.getId()).update();
        }
        return "Bearer " + jetons.emettre(utilisateur, famille).valeur();
    }

    /** Jeton d'un compte de l'équipe qui vient de se connecter : aucune fenêtre de réauthentification. */
    String accesSansReauthentification(Utilisateur utilisateur) {
        return "Bearer " + jetons.emettre(utilisateur, java.util.UUID.randomUUID()).valeur();
    }

    // ------------------------------------------------------------------ requêtes

    ResultActions envoyer(MockHttpServletRequestBuilder requete, String jeton, String acces, String corps)
            throws Exception {
        requete.header(HttpHeaders.ORIGIN, proprietes.urlSiteSansBarre());
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

    /** Nouveau panier invité contenant la variante ; renvoie le jeton du cookie. */
    String panierAvec(VarianteProduit variante, int quantite) throws Exception {
        return ajouterAuPanier(null, variante, quantite).andReturn().getResponse().getCookie(COOKIE).getValue();
    }

    ResultActions ajouterAuPanier(String jeton, VarianteProduit variante, int quantite) throws Exception {
        return envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/panier/lignes"),
                jeton, null, "{\"varianteId\":%d,\"quantite\":%d}".formatted(variante.getId(), quantite));
    }

    ResultActions lirePanier(String jeton) throws Exception {
        return mockMvc.perform(get("/api/panier").cookie(new Cookie(COOKIE, jeton)));
    }

    long totalDuPanier(String jeton) throws Exception {
        return corps(lirePanier(jeton)).path("total").asLong();
    }

    static String cle() {
        return UUID.randomUUID().toString();
    }

    /** Corps de commande à domicile : téléphone, adresse complète, paiement à la livraison. */
    String requete(String telephone, String modePaiement, long totalAttendu) {
        return requete(telephone, modePaiement, totalAttendu, "Awa Diop", "awa@exemple.sn");
    }

    String requete(String telephone, String modePaiement, long totalAttendu, String nom, String email) {
        return """
                {"nomComplet":"%s","telephone":"%s","email":%s,"modeLivraison":"DOMICILE","zoneId":%d,
                 "ville":"Dakar","quartier":"Médina","adresse":"Rue 10 x 11","pointDeRepere":"Près de la mosquée",
                 "modePaiement":"%s","totalAttendu":%d}
                """.formatted(nom, telephone, email == null ? "null" : "\"" + email + "\"", zoneId, modePaiement,
                totalAttendu);
    }

    ResultActions commander(String jeton, String acces, String cle, String corps) throws Exception {
        MockHttpServletRequestBuilder requete = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/commandes");
        if (cle != null) {
            requete.header("Idempotency-Key", cle);
        }
        return envoyer(requete, jeton, acces, corps);
    }

    JsonNode corps(ResultActions resultat) throws Exception {
        return jsonMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }

    String statut(String numero) {
        return jdbc.sql("select statut from commande where numero = :n").param("n", numero)
                .query(String.class).single();
    }
}
