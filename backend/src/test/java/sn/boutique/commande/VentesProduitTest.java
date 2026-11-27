package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.catalogue.service.AccueilService;
import sn.boutique.catalogue.service.ProprietesAccueil;
import sn.boutique.commande.facture.FactureDepot;
import sn.boutique.commande.paiement.ConfirmationPaiementService;
import sn.boutique.commande.paiement.FournisseurPaiement.Statut;
import sn.boutique.compte.modele.Role;
import sn.boutique.noyau.cache.CachesPublics;
import tools.jackson.databind.JsonNode;

/**
 * Compteur de ventes des produits et section « Meilleures ventes » : par le vrai flux (commande, paiement ou
 * livraison, facture, avoir), jamais en écrivant le compteur à la main. Les compteurs des produits créés ici sont
 * remis à zéro en fin de test (la base est partagée : règle « un test laisse la base dans l'état où il l'a trouvée »).
 */
class VentesProduitTest extends CommandeTestBase {

    private static final AtomicLong COMPTEUR = new AtomicLong(System.nanoTime() % 1_000_000 + 4_000_000);

    @Autowired
    ConfirmationPaiementService confirmation;
    @Autowired
    FactureDepot factures;
    @Autowired
    ProprietesAccueil proprietesAccueil;
    @Autowired
    CacheManager cacheManager;

    private final List<Long> produitsCrees = new ArrayList<>();

    private static String telephone() {
        return "78%07d".formatted(COMPTEUR.incrementAndGet() % 10_000_000);
    }

    @AfterEach
    void remettreLesCompteursAZero() {
        if (!produitsCrees.isEmpty()) {
            jdbc.sql("update produit set nombre_ventes = 0 where id in (:ids)").param("ids", produitsCrees).update();
            produitsCrees.clear();
        }
        CachesPublics.TOUS.forEach(nom -> cacheManager.getCache(nom).invalidate());
    }

    private VarianteProduit produit(long prix, int stock) {
        VarianteProduit variante = variante(prix, stock);
        produitsCrees.add(produitId(variante));
        return variante;
    }

    private long produitId(VarianteProduit variante) {
        return jdbc.sql("select produit_id from variante_produit where id = :i").param("i", variante.getId())
                .query(Long.class).single();
    }

    private int ventes(VarianteProduit variante) {
        return jdbc.sql("select nombre_ventes from produit where id = :i").param("i", produitId(variante))
                .query(Integer.class).single();
    }

    private String commande(VarianteProduit variante, int quantite, String mode) throws Exception {
        String jeton = panierAvec(variante, quantite);
        long total = quantite * variante.getPrix() + 2_000;
        return corps(commander(jeton, null, cle(), requete(telephone(), mode, total)).andExpect(status().isCreated()))
                .path("numero").asString();
    }

    private void payerEnLigne(String numero) throws Exception {
        long paiementId = jdbc.sql("select p.id from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = :n order by p.id desc limit 1").param("n", numero).query(Long.class).single();
        fournisseur.repondreALaVerification(jdbc.sql("select reference_externe from paiement where id = :i")
                .param("i", paiementId).query(String.class).single(), Statut.REUSSI);
        confirmation.confirmer(paiementId);
    }

    private String numeroFacture(String numeroCommande) {
        return factures.trouverParCommande(jdbc.sql("select id from commande where numero = :n")
                .param("n", numeroCommande).query(Long.class).single()).orElseThrow().numero();
    }

    private void avoir(String numeroFacture, String corps) throws Exception {
        envoyer(post("/api/admin/factures/{n}/avoirs", numeroFacture), null, acces(utilisateur(Role.ADMIN)), corps)
                .andExpect(status().isCreated());
    }

    @Test
    void uneVenteNeComptePasALaCreationDeLaCommandeMaisAuPaiementEtUneSeuleFois() throws Exception {
        VarianteProduit variante = produit(10_000, 20);
        String numero = commande(variante, 3, "MOBILE_MONEY");
        assertThat(ventes(variante)).as("commande créée, non payée").isZero();

        payerEnLigne(numero);
        assertThat(ventes(variante)).as("paiement confirmé : 3 unités").isEqualTo(3);

        // Notification reçue de nouveau : rien de plus
        payerEnLigne(numero);
        assertThat(ventes(variante)).isEqualTo(3);
        // Seconde commande payée : les ventes s'additionnent
        payerEnLigne(commandeEtPayee(variante, 2));
        assertThat(ventes(variante)).isEqualTo(5);
    }

    private String commandeEtPayee(VarianteProduit variante, int quantite) throws Exception {
        String numero = commande(variante, quantite, "MOBILE_MONEY");
        payerEnLigne(numero);
        return numero;
    }

    @Test
    void uneCommandeALaLivraisonNeCompteQueQuandElleEstLivree() throws Exception {
        VarianteProduit variante = produit(10_000, 20);
        String numero = commande(variante, 2, "A_LA_LIVRAISON");
        String agent = acces(utilisateur(Role.SERVICE_CLIENT));
        envoyer(post("/api/admin/commandes/{n}/confirmer", numero), null, agent, null).andExpect(status().isOk());
        assertThat(ventes(variante)).as("confirmée par téléphone, pas encore livrée").isZero();

        envoyer(post("/api/admin/commandes/{n}/livrer", numero), null, agent, """
                {"montantRecu":22000,"moyen":"ESPECES"}
                """).andExpect(status().isOk());

        assertThat(ventes(variante)).isEqualTo(2);
    }

    @Test
    void unAvoirRetireLesUnitesCrediteesSansJamaisDescendreSousZero() throws Exception {
        VarianteProduit variante = produit(10_000, 20);
        String numero = commandeEtPayee(variante, 4);
        String facture = numeroFacture(numero);
        assertThat(ventes(variante)).isEqualTo(4);

        avoir(facture, "{\"motif\":\"Une unité défectueuse\",\"lignes\":[{\"rang\":1,\"quantite\":1}]}");
        assertThat(ventes(variante)).isEqualTo(3);

        // La livraison créditée seule ne retire aucune unité
        avoir(facture, "{\"motif\":\"Livraison offerte\",\"lignes\":[{\"rang\":2,\"quantite\":1}]}");
        assertThat(ventes(variante)).isEqualTo(3);

        avoir(facture, "{\"motif\":\"Reste de la commande\",\"total\":true}");
        assertThat(ventes(variante)).isZero();

        // Compteur déjà ramené à zéro ailleurs (retour manuel) : un avoir ne le rend jamais négatif
        VarianteProduit autre = produit(10_000, 20);
        String factureAutre = numeroFacture(commandeEtPayee(autre, 2));
        jdbc.sql("update produit set nombre_ventes = 0 where id = :i").param("i", produitId(autre)).update();
        avoir(factureAutre, "{\"motif\":\"Retour\",\"total\":true}");
        assertThat(ventes(autre)).isZero();
    }

    @Test
    void modifierUnProduitNeReecritJamaisSonCompteurDeVentes() throws Exception {
        VarianteProduit variante = produit(10_000, 20);
        payerEnLigne(commande(variante, 2, "MOBILE_MONEY"));
        long produitId = produitId(variante);

        // Un produit chargé avant la vente, puis enregistré après : le compteur reste celui de la base
        transaction.executeWithoutResult(statut -> {
            var produit = em.find(sn.boutique.catalogue.modele.Produit.class, produitId);
            jdbc.sql("update produit set nombre_ventes = nombre_ventes + 5 where id = :i").param("i", produitId).update();
            produit.setNom(produit.getNom() + " (modifié)");
            em.flush();
        });

        assertThat(ventes(variante)).isEqualTo(7);
    }

    @Test
    void lesMeilleuresVentesApparaissentQuandAssezDeProduitsSontVendusParLeVraiFlux() throws Exception {
        int seuil = proprietesAccueil.seuilMeilleuresVentes();
        long dejaVendus = jdbc.sql("select count(*) from produit where actif and nombre_ventes > 0")
                .query(Long.class).single();

        // Sous le seuil : pas de « Meilleures ventes »
        if (dejaVendus < seuil) {
            assertThat(codes(accueil())).doesNotContain(AccueilService.MEILLEURES_VENTES);
        }

        // Le produit vedette est vendu jusqu'à dépasser tous les autres, puis assez d'autres produits pour atteindre le seuil
        VarianteProduit vedette = produit(10_000, 200);
        for (int essai = 0; essai < 20 && ventes(vedette) <= maxAutres(produitId(vedette)); essai++) {
            commandeEtPayee(vedette, 5);
        }
        long manquants = Math.max(0, seuil - jdbc.sql("select count(*) from produit where actif and nombre_ventes > 0")
                .query(Long.class).single());
        for (long i = 0; i < manquants; i++) {
            commandeEtPayee(produit(10_000, 5), 1);
        }

        JsonNode sections = accueil();
        assertThat(codes(sections)).contains(AccueilService.MEILLEURES_VENTES).doesNotContain(AccueilService.SELECTION);
        JsonNode meilleures = null;
        for (JsonNode section : sections) {
            if (AccueilService.MEILLEURES_VENTES.equals(section.get("code").asString())) {
                meilleures = section;
            }
        }
        assertThat(meilleures).isNotNull();
        assertThat(meilleures.get("titre").asString()).isEqualTo("Meilleures ventes");
        assertThat(meilleures.get("produits").get(0).get("id").asLong()).isEqualTo(produitId(vedette));
    }

    private long maxAutres(long produitId) {
        return jdbc.sql("select coalesce(max(nombre_ventes), 0) from produit where id <> :i").param("i", produitId)
                .query(Long.class).single();
    }

    private JsonNode accueil() throws Exception {
        CachesPublics.TOUS.forEach(nom -> cacheManager.getCache(nom).invalidate());
        return corps(mockMvc.perform(get("/api/accueil")).andExpect(status().isOk())).get("sections");
    }

    private static List<String> codes(JsonNode sections) {
        List<String> codes = new ArrayList<>();
        sections.forEach(s -> codes.add(s.get("code").asString()));
        return codes;
    }
}
