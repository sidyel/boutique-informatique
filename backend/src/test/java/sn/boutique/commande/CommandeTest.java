package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.marketing.modele.TypeCodePromo;
import tools.jackson.databind.JsonNode;

/**
 * Création d'une commande de bout en bout : stock réservé en une transaction, idempotence, anti-survente,
 * contrôles de prix et de total, paiement à la livraison, code promo réservé, attribution.
 */
class CommandeTest extends CommandeTestBase {

    private static final AtomicLong COMPTEUR = new AtomicLong(System.nanoTime() % 1_000_000);

    /** Numéro sénégalais valide et unique par appel (les commandes persistent d'un test à l'autre). */
    private static String telephone() {
        return "77%07d".formatted(COMPTEUR.incrementAndGet() % 10_000_000);
    }

    // ------------------------------------------------------------------ cas nominaux

    @Test
    void commandeALaLivraisonReserveLeStockEtVideLePanier() throws Exception {
        VarianteProduit variante = variante(150_000, 10);
        String jeton = panierAvec(variante, 2);
        String tel = telephone();

        JsonNode reponse = corps(commander(jeton, null, cle(), requete(tel, "A_LA_LIVRAISON", 302_000))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_CONFIRMATION"))
                .andExpect(jsonPath("$.total").value(302_000))
                .andExpect(jsonPath("$.urlPaiement").doesNotExist())
                .andExpect(jsonPath("$.rejouee").value(false)));

        String numero = reponse.path("numero").asString();
        assertThat(numero).matches("CMD-\\d{4}-\\d{6}");
        assertThat(reponse.path("jetonSuivi").asString()).matches("[A-Za-z0-9_-]{43}");
        // Stock : 10 − 2, produit relu (stock_total par trigger), mouvement SORTIE_VENTE lié à la commande
        assertThat(stock(variante)).isEqualTo(8);
        assertThat(stockTotalProduit(variante)).isEqualTo(8);
        assertThat(compter("""
                select count(*) from mouvement_stock m join commande c on c.id = m.commande_id
                 where c.numero = ? and m.type = 'SORTIE_VENTE' and m.quantite = -2 and m.stock_apres = 8
                """, numero)).isEqualTo(1);
        // Commande : lignes figées, adresse, délai de 24 h, historique
        assertThat(jdbc.sql("select sous_total, frais_livraison, remise, total, nom_client, email_client, "
                        + "telephone_client from commande where numero = :n").param("n", numero)
                .query((rs, i) -> List.of(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                        rs.getString(5), rs.getString(6), rs.getString(7))).single())
                .containsExactly(300_000L, 2_000L, 0L, 302_000L, "Awa Diop", "awa@exemple.sn", "+221" + tel);
        assertThat(compter("select count(*) from ligne_commande l join commande c on c.id = l.commande_id "
                + "where c.numero = ? and l.sku = ? and l.prix_unitaire = 150000 and l.quantite = 2",
                numero, variante.getSku())).isEqualTo(1);
        assertThat(jdbc.sql("select extract(epoch from expire_le - date_creation)::bigint from commande where numero = :n")
                .param("n", numero).query(Long.class).single()).isEqualTo(24 * 3600);
        assertThat(compter("select count(*) from historique_statut_commande h join commande c on c.id = h.commande_id "
                + "where c.numero = ? and h.ancien_statut is null and h.nouveau_statut = 'EN_ATTENTE_CONFIRMATION'",
                numero)).isEqualTo(1);
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.fournisseur = 'A_LA_LIVRAISON' and p.statut = 'EN_ATTENTE'", numero))
                .isEqualTo(1);
        // Le panier est vide
        lirePanier(jeton).andExpect(jsonPath("$.lignes.length()").value(0));
    }

    @Test
    void commandeEnLigneDemarreLePaiementEtLeStockEstReserveUneSeuleFois() throws Exception {
        VarianteProduit variante = variante(100_000, 5);
        String jeton = panierAvec(variante, 1);

        JsonNode reponse = corps(commander(jeton, null, cle(), requete(telephone(), "MOBILE_MONEY", 102_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_PAIEMENT"))
                .andExpect(jsonPath("$.paiementAReprendre").value(false))
                .andExpect(jsonPath("$.urlPaiement").value(org.hamcrest.Matchers.startsWith("https://paiement.test/")))
                .andExpect(jsonPath("$.expireLe").exists()));

        String numero = reponse.path("numero").asString();
        assertThat(stock(variante)).isEqualTo(4);
        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");
        assertThat(jdbc.sql("select extract(epoch from expire_le - date_creation)::bigint from commande where numero = :n")
                .param("n", numero).query(Long.class).single()).isEqualTo(30 * 60);
        // Le prestataire reçoit le total, le numéro, le téléphone et des URL de retour sur notre site
        assertThat(fournisseur.demandes()).singleElement().satisfies(demande -> {
            assertThat(demande.montant()).isEqualTo(102_000);
            assertThat(demande.numeroCommande()).isEqualTo(numero);
            // Adresse de retour indépendante du jeton de suivi (qui peut être renouvelé) : jeton de retour dédié
            assertThat(demande.urlRetour()).contains("/commande/retour/")
                    .doesNotContain(reponse.path("jetonSuivi").asString()).doesNotContain("/suivi/");
            assertThat(demande.urlAnnulation()).isEqualTo(demande.urlRetour() + "?annule=1");
            assertThat(demande.urlNotification()).endsWith("/api/paiements/paydunya/webhook");
        });
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.fournisseur = 'PAYDUNYA' and p.statut = 'EN_ATTENTE' "
                + "and p.url_paiement like 'https://paiement.test/%'", numero)).isEqualTo(1);
    }

    @Test
    void lePaiementEnLigneNEstPasProposeSansPrestataire() throws Exception {
        fournisseur.configurer(false);
        VarianteProduit variante = variante(100_000, 5);
        String jeton = panierAvec(variante, 1);

        commander(jeton, null, cle(), requete(telephone(), "MOBILE_MONEY", 102_000))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MODE_PAIEMENT_INDISPONIBLE"));
        assertThat(stock(variante)).isEqualTo(5);
        mockMvc.perform(MockMvcRequestBuilders.get("/api/commandes/options"))
                .andExpect(jsonPath("$.paiementEnLigne").value(false))
                .andExpect(jsonPath("$.paiementLivraison").value(true))
                .andExpect(jsonPath("$.paiementLivraisonMax").value(500_000))
                .andExpect(jsonPath("$.retraitMagasin").value(false));
    }

    @Test
    void siLePrestataireNeRepondPasLaCommandeExisteEtLePaiementSeReprend() throws Exception {
        fournisseur.tomberEnPanne(true);
        VarianteProduit variante = variante(100_000, 5);
        String jeton = panierAvec(variante, 1);

        JsonNode reponse = corps(commander(jeton, null, cle(), requete(telephone(), "MOBILE_MONEY", 102_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.urlPaiement").doesNotExist())
                .andExpect(jsonPath("$.paiementAReprendre").value(true)));
        String numero = reponse.path("numero").asString();
        String suivi = reponse.path("jetonSuivi").asString();
        assertThat(stock(variante)).isEqualTo(4);

        // Le prestataire revient : « Reprendre le paiement » depuis la page de suivi, sans nouvelle réservation
        fournisseur.tomberEnPanne(false);
        envoyer(MockMvcRequestBuilders.post("/api/commandes/suivi/{jeton}/reprise-paiement", suivi), null, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPaiement").value(org.hamcrest.Matchers.startsWith("https://paiement.test/")));
        assertThat(stock(variante)).isEqualTo(4);
        assertThat(compter("select count(*) from mouvement_stock m join commande c on c.id = m.commande_id "
                + "where c.numero = ?", numero)).isEqualTo(1);
        // Une seconde reprise retrouve la facture encore valide : jamais deux factures payables
        envoyer(MockMvcRequestBuilders.post("/api/commandes/suivi/{jeton}/reprise-paiement", suivi), null, null, null)
                .andExpect(status().isOk());
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.fournisseur = 'PAYDUNYA'", numero)).isEqualTo(1);
        assertThat(fournisseur.demandes()).hasSize(1);
    }

    // ------------------------------------------------------------------ idempotence

    @Test
    void laMemeCleRenvoieLaMemeCommandeSansSecondeReservation() throws Exception {
        VarianteProduit variante = variante(100_000, 5);
        String jeton = panierAvec(variante, 1);
        String cle = cle();
        String corps = requete(telephone(), "MOBILE_MONEY", 102_000);

        JsonNode premiere = corps(commander(jeton, null, cle, corps).andExpect(status().isCreated()));
        JsonNode seconde = corps(commander(jeton, null, cle, corps).andExpect(status().isOk())
                .andExpect(jsonPath("$.rejouee").value(true)));

        assertThat(seconde.path("numero").asString()).isEqualTo(premiere.path("numero").asString());
        // Le jeton n'est pas stocké (seul son hash l'est) : le rejeu renvoie un nouveau jeton qui remplace l'ancien
        assertThat(seconde.path("jetonSuivi").asString()).matches("[A-Za-z0-9_-]{43}")
                .isNotEqualTo(premiere.path("jetonSuivi").asString());
        // Même page de paiement, pas de seconde facture ; une seule commande ; stock réservé une seule fois
        assertThat(seconde.path("urlPaiement").asString()).isEqualTo(premiere.path("urlPaiement").asString());
        assertThat(fournisseur.demandes()).hasSize(1);
        assertThat(stock(variante)).isEqualTo(4);
        assertThat(compter("select count(*) from commande where numero = ?", premiere.path("numero").asString()))
                .isEqualTo(1);
    }

    @Test
    void laMemeCleAvecUneAutreDemandeEstRefuseeEtLaCleEstObligatoire() throws Exception {
        VarianteProduit variante = variante(100_000, 5);
        String jeton = panierAvec(variante, 1);
        String cle = cle();
        String tel = telephone();
        commander(jeton, null, cle, requete(tel, "A_LA_LIVRAISON", 102_000)).andExpect(status().isCreated());

        commander(jeton, null, cle, requete(tel, "A_LA_LIVRAISON", 999_999))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLE_IDEMPOTENCE_REUTILISEE"));
        commander(jeton, null, null, requete(tel, "A_LA_LIVRAISON", 102_000))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLE_IDEMPOTENCE_INVALIDE"));
        commander(jeton, null, "court", requete(tel, "A_LA_LIVRAISON", 102_000))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLE_IDEMPOTENCE_INVALIDE"));
    }

    @Test
    void uneCleDUnAutreClientNeRetrouvePasSaCommande() throws Exception {
        VarianteProduit variante = variante(100_000, 5);
        String jetonA = panierAvec(variante, 1);
        String jetonB = panierAvec(variante, 1);
        String cle = cle();
        String corps = requete(telephone(), "A_LA_LIVRAISON", 102_000);

        String numeroA = corps(commander(jetonA, null, cle, corps).andExpect(status().isCreated()))
                .path("numero").asString();
        // Même clé, autre panier : la clé est liée au client, une vraie seconde commande est créée
        String numeroB = corps(commander(jetonB, null, cle, corps).andExpect(status().isCreated()))
                .path("numero").asString();

        assertThat(numeroB).isNotEqualTo(numeroA);
        assertThat(stock(variante)).isEqualTo(3);
    }

    @Test
    void deuxRequetesSimultaneesAvecLaMemeCleCreentUneSeuleCommande() throws Exception {
        VarianteProduit variante = variante(100_000, 5);
        String jeton = panierAvec(variante, 2);
        String cle = cle();
        String corps = requete(telephone(), "A_LA_LIVRAISON", 202_000);

        List<JsonNode> reponses = enParallele(
                () -> corps(commander(jeton, null, cle, corps)),
                () -> corps(commander(jeton, null, cle, corps)));

        assertThat(reponses.stream().map(r -> r.path("numero").asString()).distinct()).hasSize(1);
        assertThat(reponses.stream().filter(r -> r.path("rejouee").asBoolean())).hasSize(1);
        assertThat(stock(variante)).isEqualTo(3); // 5 − 2, une seule fois
        assertThat(compter("select count(*) from mouvement_stock where variante_id = ? and type = 'SORTIE_VENTE'",
                variante.getId())).isEqualTo(1);
        assertThat(compter("select count(*) from commande where numero = ?",
                reponses.getFirst().path("numero").asString())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ anti-survente

    @Test
    void laDerniereUniteDisputeeNeVaQuAUnClient() throws Exception {
        VarianteProduit variante = variante(100_000, 1);
        String jetonA = panierAvec(variante, 1);
        String jetonB = panierAvec(variante, 1);

        List<Integer> statuts = enParallele(
                () -> commander(jetonA, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 102_000))
                        .andReturn().getResponse().getStatus(),
                () -> commander(jetonB, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 102_000))
                        .andReturn().getResponse().getStatus());

        assertThat(statuts).containsExactlyInAnyOrder(201, 409);
        assertThat(stock(variante)).isZero();
        assertThat(stockTotalProduit(variante)).isZero();
        assertThat(compter("select count(*) from ligne_commande where variante_id = ?", variante.getId()))
                .isEqualTo(1);
        assertThat(compter("select count(*) from mouvement_stock where variante_id = ? and type = 'SORTIE_VENTE'",
                variante.getId())).isEqualTo(1);
    }

    @Test
    void laCommandeEchoueEntierementSiUneLigneNEstPlusDisponible() throws Exception {
        VarianteProduit abondante = variante(10_000, 10);
        VarianteProduit rare = variante(20_000, 3);
        String jeton = panierAvec(abondante, 1);
        ajouterAuPanier(jeton, rare, 3);
        // Entre le panier et la commande, quelqu'un d'autre achète 2 exemplaires de l'article rare
        jdbc.sql("update variante_produit set stock = 1, version = version + 1 where id = :id")
                .param("id", rare.getId()).update();

        commander(jeton, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 72_000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PANIER_NON_COMMANDABLE"));

        // Aucune réservation partielle, panier intact
        assertThat(stock(abondante)).isEqualTo(10);
        assertThat(stock(rare)).isEqualTo(1);
        assertThat(corps(lirePanier(jeton)).path("lignes").size()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ prix et total

    @Test
    void unPrixChangeEntrePanierEtCommandeBloqueLaCommande() throws Exception {
        VarianteProduit variante = variante(100_000, 5);
        String jeton = panierAvec(variante, 1);
        jdbc.sql("update variante_produit set prix = 110000, version = version + 1 where id = :id")
                .param("id", variante.getId()).update();

        commander(jeton, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 102_000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRIX_MODIFIES"));
        assertThat(stock(variante)).isEqualTo(5);

        // Le client accepte les nouveaux prix puis commande au nouveau total
        envoyer(MockMvcRequestBuilders.post("/api/panier/accepter-prix"), jeton, null, null).andExpect(status().isOk());
        commander(jeton, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 112_000))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(112_000));
    }

    @Test
    void leTotalAttenduDoitCorrespondreAuTotalDuServeur() throws Exception {
        VarianteProduit variante = variante(100_000, 5);
        String jeton = panierAvec(variante, 1);

        commander(jeton, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 100_000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOTAL_MODIFIE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("102")));
        assertThat(stock(variante)).isEqualTo(5);
    }

    @Test
    void lesFraisDeLivraisonViennentDeLaZoneDuServeur() throws Exception {
        VarianteProduit variante = variante(100_000, 5);
        String jeton = panierAvec(variante, 1);
        long zoneChere = enTransaction(e -> {
            var zone = new sn.boutique.commande.modele.ZoneLivraison(
                    sn.boutique.support.FabriqueCatalogue.unique("Thiès"), 5_000, 2);
            e.persist(zone);
            return zone.getId();
        });
        String corps = requete(telephone(), "A_LA_LIVRAISON", 105_000).replace("\"zoneId\":" + zoneId,
                "\"zoneId\":" + zoneChere);

        commander(jeton, null, cle(), corps).andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(105_000));
        assertThat(jdbc.sql("select frais_livraison from commande order by id desc limit 1")
                .query(Long.class).single()).isEqualTo(5_000);
    }

    // ------------------------------------------------------------------ livraison

    @Test
    void laLivraisonADomicileExigeUneZoneEtUneAdresse() throws Exception {
        VarianteProduit variante = variante(100_000, 5);
        String jeton = panierAvec(variante, 1);
        String tel = telephone();

        commander(jeton, null, cle(), requete(tel, "A_LA_LIVRAISON", 102_000)
                .replace("\"zoneId\":" + zoneId, "\"zoneId\":null"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ZONE_INVALIDE"));
        commander(jeton, null, cle(), requete(tel, "A_LA_LIVRAISON", 102_000)
                .replace("\"quartier\":\"Médina\"", "\"quartier\":\"\""))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ADRESSE_INCOMPLETE"));
        assertThat(stock(variante)).isEqualTo(5);
    }

    @Test
    void leRetraitEnBoutiqueExigeUneBoutiquePhysique() throws Exception {
        VarianteProduit variante = variante(100_000, 5);
        String jeton = panierAvec(variante, 1);
        String corps = """
                {"nomComplet":"Awa Diop","telephone":"%s","modeLivraison":"RETRAIT_MAGASIN",
                 "modePaiement":"A_LA_LIVRAISON","totalAttendu":100000}
                """.formatted(telephone());

        commander(jeton, null, cle(), corps).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RETRAIT_INDISPONIBLE"));

        jdbc.sql("""
                update parametres_site set boutique_physique = true, boutique_rue = 'Avenue Cheikh Anta Diop',
                       boutique_ville = 'Dakar', boutique_region = 'Dakar' where id = 1
                """).update();
        mockMvc.perform(MockMvcRequestBuilders.get("/api/commandes/options"))
                .andExpect(jsonPath("$.retraitMagasin").value(true))
                .andExpect(jsonPath("$.boutiqueAdresse").value("Avenue Cheikh Anta Diop, Dakar"));
        // Sans e-mail (facultatif), sans adresse, frais nuls
        commander(jeton, null, cle(), corps).andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(100_000));
        assertThat(jdbc.sql("select email_client is null and livraison_ville is null and frais_livraison = 0 "
                + "and zone_livraison_id is null from commande order by id desc limit 1")
                .query(Boolean.class).single()).isTrue();
    }

    // ------------------------------------------------------------------ paiement à la livraison

    @Test
    void lePaiementALaLivraisonEstPlafonne() throws Exception {
        VarianteProduit variante = variante(600_000, 5);
        String jeton = panierAvec(variante, 1);

        commander(jeton, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 602_000))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAIEMENT_LIVRAISON_MONTANT_MAX"));

        // Le plafond est réglable sans redéploiement ; à la limite exacte, c'est accepté
        jdbc.sql("update parametres_site set paiement_livraison_montant_max = 602000 where id = 1").update();
        commander(jeton, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 602_000)).andExpect(status().isCreated());
    }

    @Test
    void unMemeNumeroNePeutAvoirQueDeuxCommandesNonConfirmees() throws Exception {
        String tel = telephone();
        for (int i = 0; i < 2; i++) {
            String jeton = panierAvec(variante(10_000, 5), 1);
            commander(jeton, null, cle(), requete(tel, "A_LA_LIVRAISON", 12_000)).andExpect(status().isCreated());
        }
        VarianteProduit variante = variante(10_000, 5);
        String jeton = panierAvec(variante, 1);

        commander(jeton, null, cle(), requete(tel, "A_LA_LIVRAISON", 12_000))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAIEMENT_LIVRAISON_LIMITE"));
        assertThat(stock(variante)).isEqualTo(5);
        // Le paiement en ligne reste possible, et un autre numéro n'est pas concerné
        commander(jeton, null, cle(), requete(tel, "MOBILE_MONEY", 12_000)).andExpect(status().isCreated());
        String autre = panierAvec(variante(10_000, 5), 1);
        commander(autre, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 12_000)).andExpect(status().isCreated());
    }

    @Test
    void lePaiementALaLivraisonPeutEtreDesactive() throws Exception {
        jdbc.sql("update parametres_site set paiement_livraison_actif = false where id = 1").update();
        String jeton = panierAvec(variante(10_000, 5), 1);

        commander(jeton, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 12_000))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MODE_PAIEMENT_INDISPONIBLE"));
    }

    // ------------------------------------------------------------------ code promo

    @Test
    void lUtilisationDuCodePromoEstReserveeALaCommandeEtLeDernierUsageNePasseQueUneFois() throws Exception {
        String code = code(TypeCodePromo.MONTANT_FIXE, 5_000, c -> c.setUtilisationsMax(1));
        VarianteProduit variante = variante(100_000, 10);
        String jetonA = panierAvec(variante, 1);
        String jetonB = panierAvec(variante, 1);
        envoyer(MockMvcRequestBuilders.put("/api/panier/code-promo"), jetonA, null, "{\"code\":\"%s\"}".formatted(code));
        envoyer(MockMvcRequestBuilders.put("/api/panier/code-promo"), jetonB, null, "{\"code\":\"%s\"}".formatted(code));
        assertThat(totalDuPanier(jetonA)).isEqualTo(95_000);

        List<Integer> statuts = enParallele(
                () -> commander(jetonA, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 97_000))
                        .andReturn().getResponse().getStatus(),
                () -> commander(jetonB, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 97_000))
                        .andReturn().getResponse().getStatus());

        assertThat(statuts).containsExactlyInAnyOrder(201, 422);
        assertThat(compter("select nombre_utilisations from code_promo where code = ?", code)).isEqualTo(1);
        // Le refus ne laisse aucune trace : un seul exemplaire sorti du stock, une seule commande avec le code
        assertThat(stock(variante)).isEqualTo(9);
        assertThat(compter("select count(*) from commande where code_promo = ? and remise = 5000", code)).isEqualTo(1);
    }

    @Test
    void unCodePromoDevenuExpireRefuseLaCommande() throws Exception {
        String code = code(TypeCodePromo.POURCENTAGE, 10, c -> c.setDateFin(horloge.instant().plus(Duration.ofHours(1))));
        VarianteProduit variante = variante(100_000, 10);
        String jeton = panierAvec(variante, 1);
        envoyer(MockMvcRequestBuilders.put("/api/panier/code-promo"), jeton, null, "{\"code\":\"%s\"}".formatted(code));
        horloge.avancer(Duration.ofHours(2));

        commander(jeton, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 102_000))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_PROMO_EXPIRE"));
        assertThat(stock(variante)).isEqualTo(10);
    }

    @Test
    void lePlafondParClientDUnInviteSeVerifieSurLeTelephone() throws Exception {
        String code = code(TypeCodePromo.MONTANT_FIXE, 5_000, c -> c.setUtilisationsParClient(1));
        VarianteProduit variante = variante(100_000, 10);
        String tel = telephone();
        String premier = panierAvec(variante, 1);
        envoyer(MockMvcRequestBuilders.put("/api/panier/code-promo"), premier, null, "{\"code\":\"%s\"}".formatted(code));
        commander(premier, null, cle(), requete(tel, "A_LA_LIVRAISON", 97_000)).andExpect(status().isCreated());

        String second = panierAvec(variante, 1);
        envoyer(MockMvcRequestBuilders.put("/api/panier/code-promo"), second, null, "{\"code\":\"%s\"}".formatted(code));
        commander(second, null, cle(), requete(tel, "A_LA_LIVRAISON", 97_000))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_PROMO_DEJA_UTILISE"));
        // Le refus rend l'utilisation réservée par cette tentative
        assertThat(compter("select nombre_utilisations from code_promo where code = ?", code)).isEqualTo(1);
        // Un autre numéro peut l'utiliser
        commander(second, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 97_000)).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ attribution et sécurité

    @Test
    void lAttributionEstNettoyeeEtLesIdentifiantsPublicitairesSontIgnoresSansConsentement() throws Exception {
        String jeton = panierAvec(variante(100_000, 5), 1);
        String base = requete(telephone(), "A_LA_LIVRAISON", 102_000).strip();
        String corps = base.substring(0, base.length() - 1) + """
                ,"attribution":{"utmSource":"facebook","utmMedium":"cpc","utmCampagne":"soldes\\u0000\\n-novembre",
                 "pageArrivee":"/categorie/pc-portables?utm_source=facebook","referent":"https://l.facebook.com/",
                 "premiereSource":"google","premierMedium":"organic","gclid":"abc123","fbclid":"def456",
                 "fbp":"fb.1.1","fbc":"fb.1.2"}}
                """;

        commander(jeton, null, cle(), corps).andExpect(status().isCreated());

        assertThat(jdbc.sql("""
                select utm_source || '|' || utm_medium || '|' || utm_campagne || '|' || page_arrivee || '|'
                       || premiere_source || '|' || premier_medium,
                       gclid is null and fbclid is null and fbp is null and fbc is null, user_agent
                  from commande order by id desc limit 1
                """).query((rs, i) -> List.of(rs.getString(1), rs.getBoolean(2))).single())
                .containsExactly("facebook|cpc|soldes  -novembre|/categorie/pc-portables?utm_source=facebook"
                        + "|google|organic", true);
    }

    @Test
    void lAdresseIpEtLeNavigateurSontEnregistres() throws Exception {
        String jeton = panierAvec(variante(100_000, 5), 1);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/commandes")
                        .header(HttpHeaders.ORIGIN, proprietes.urlSiteSansBarre())
                        .header("Idempotency-Key", cle())
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) Essai")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE, jeton))
                        .contentType(MediaType.APPLICATION_JSON).content(requete(telephone(), "A_LA_LIVRAISON", 102_000)))
                .andExpect(status().isCreated());

        assertThat(jdbc.sql("select adresse_ip, user_agent from commande order by id desc limit 1")
                .query((rs, i) -> List.of(rs.getString(1), rs.getString(2))).single())
                .containsExactly("127.0.0.1", "Mozilla/5.0 (Linux; Android 13) Essai");
    }

    @Test
    void unPostSansOriginEstRefuse() throws Exception {
        String jeton = panierAvec(variante(100_000, 5), 1);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/commandes").header("Idempotency-Key", cle())
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE, jeton))
                        .contentType(MediaType.APPLICATION_JSON).content(requete(telephone(), "A_LA_LIVRAISON", 102_000)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGINE_REFUSEE"));
    }

    @Test
    void unPanierVideOuInconnuNeDonneAucuneCommande() throws Exception {
        commander(null, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 0))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("PANIER_VIDE"));
        commander("a".repeat(43), null, cle(), requete(telephone(), "A_LA_LIVRAISON", 0))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("PANIER_VIDE"));
    }

    @Test
    void lesChampsInvalidesSontRefusesAvecLeurErreur() throws Exception {
        String jeton = panierAvec(variante(100_000, 5), 1);

        commander(jeton, null, cle(), requete("12345", "A_LA_LIVRAISON", 102_000).replace("awa@exemple.sn", "pas-un-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='telephone')]").exists())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='email')]").exists());
    }

    @Test
    void unClientConnecteCommandeAvecSonPanierEtSonAdresseEmail() throws Exception {
        var client = utilisateur(sn.boutique.compte.modele.Role.CLIENT);
        String acces = acces(client);
        VarianteProduit variante = variante(100_000, 5);
        envoyer(MockMvcRequestBuilders.post("/api/panier/lignes"), null,acces,
                "{\"varianteId\":%d,\"quantite\":1}".formatted(variante.getId())).andExpect(status().isOk());

        commander(null, acces, cle(), requete(telephone(), "A_LA_LIVRAISON", 102_000).replace("\"awa@exemple.sn\"", "null"))
                .andExpect(status().isCreated());

        assertThat(jdbc.sql("select email_client, utilisateur_id from commande order by id desc limit 1")
                .query((rs, i) -> List.of(rs.getString(1), rs.getLong(2))).single())
                .containsExactly(client.getEmail(), client.getId());
    }

    // ------------------------------------------------------------------ outils

    /** Exécute les actions au même instant, chacune dans son propre fil. */
    @SafeVarargs
    private static <T> List<T> enParallele(Callable<T>... actions) throws Exception {
        ExecutorService executeur = Executors.newFixedThreadPool(actions.length);
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<T>> futurs = java.util.Arrays.stream(actions).map(action -> executeur.submit(() -> {
                depart.await();
                return action.call();
            })).toList();
            depart.countDown();
            java.util.ArrayList<T> resultats = new java.util.ArrayList<>();
            for (Future<T> futur : futurs) {
                resultats.add(futur.get());
            }
            return resultats;
        } finally {
            executeur.shutdownNow();
        }
    }
}
