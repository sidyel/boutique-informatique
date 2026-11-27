package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.service.ExpirationCommandes;
import sn.boutique.commande.service.NotificationsCommande;
import sn.boutique.compte.modele.Role;
import sn.boutique.marketing.modele.TypeCodePromo;
import sn.boutique.support.ClientMailpit;
import sn.boutique.support.FauxNotificationClient;
import tools.jackson.databind.JsonNode;

/**
 * Vie d'une commande après sa création : confirmation et annulation par le service client, expiration de la
 * réservation, page de suivi, notifications.
 */
class GestionCommandeTest extends CommandeTestBase {

    private static final AtomicLong COMPTEUR = new AtomicLong(System.nanoTime() % 1_000_000 + 5_000_000);

    @Autowired
    ExpirationCommandes expiration;
    @Autowired
    NotificationsCommande notificationsCommande;
    @Value("${test.mailpit.url}")
    String urlMailpit;

    private static String telephone() {
        return "78%07d".formatted(COMPTEUR.incrementAndGet() % 10_000_000);
    }

    /** Commande à la livraison de 2 exemplaires à 10 000 FCFA ; renvoie sa réponse (numero, jetonSuivi…). */
    private JsonNode commandeALaLivraison(VarianteProduit variante, int quantite, long total) throws Exception {
        String jeton = panierAvec(variante, quantite);
        return corps(commander(jeton, null, cle(), requete(telephone(), "A_LA_LIVRAISON", total))
                .andExpect(status().isCreated()));
    }

    private JsonNode commandeEnLigne(VarianteProduit variante, long total) throws Exception {
        String jeton = panierAvec(variante, 1);
        return corps(commander(jeton, null, cle(), requete(telephone(), "MOBILE_MONEY", total))
                .andExpect(status().isCreated()));
    }

    private ResultActions admin(String numero, String action, String acces, String corps)
            throws Exception {
        return envoyer(post("/api/admin/commandes/{numero}/{action}", numero, action), null, acces, corps);
    }

    // ------------------------------------------------------------------ confirmation

    @Test
    void leServiceClientConfirmeUneCommandeALaLivraison() throws Exception {
        VarianteProduit variante = variante(10_000, 10);
        String numero = commandeALaLivraison(variante, 2, 22_000).path("numero").asString();
        var agent = utilisateur(Role.SERVICE_CLIENT);

        // Elle apparaît dans la liste « à confirmer » avec le téléphone complet pour rappeler le client
        mockMvc.perform(get("/api/admin/commandes").header(HttpHeaders.AUTHORIZATION, acces(agent)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.elements[?(@.numero=='%s')].telephone".formatted(numero))
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.startsWith("+22178"))));

        admin(numero, "confirmer", acces(agent), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CONFIRMEE"));

        assertThat(statut(numero)).isEqualTo("CONFIRMEE");
        assertThat(jdbc.sql("select expire_le is null from commande where numero = :n").param("n", numero)
                .query(Boolean.class).single()).isTrue();
        assertThat(stock(variante)).isEqualTo(8); // le stock reste sorti
        assertThat(compter("""
                select count(*) from historique_statut_commande h join commande c on c.id = h.commande_id
                 where c.numero = ? and h.nouveau_statut = 'CONFIRMEE' and h.auteur = ?
                """, numero, agent.getEmail())).isEqualTo(1);

        // Une commande confirmée n'est plus ni confirmable ni expirée par la tâche planifiée
        admin(numero, "confirmer", acces(agent), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATUT_INVALIDE"));
        horloge.avancer(Duration.ofDays(3));
        assertThat(expiration.expirer()).isZero();
        assertThat(statut(numero)).isEqualTo("CONFIRMEE");
        assertThat(stock(variante)).isEqualTo(8);
    }

    @Test
    void seulsLesRolesInternesGerentLesCommandes() throws Exception {
        String numero = commandeALaLivraison(variante(10_000, 10), 1, 12_000).path("numero").asString();

        admin(numero, "confirmer", acces(utilisateur(Role.CLIENT)), null).andExpect(status().isForbidden());
        admin(numero, "annuler", acces(utilisateur(Role.CLIENT)), "{\"motif\":\"x\"}")
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/commandes").header(HttpHeaders.AUTHORIZATION, acces(utilisateur(Role.CLIENT))))
                .andExpect(status().isForbidden());
        admin(numero, "confirmer", null, null).andExpect(status().isUnauthorized());
        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_CONFIRMATION");
        admin("CMD-2026-999999", "confirmer", acces(utilisateur(Role.SERVICE_CLIENT)), null)
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ annulation

    @Test
    void lAnnulationRemetLeStockEnVenteRendLeCodePromoEtAbandonneLesPaiements() throws Exception {
        String code = code(TypeCodePromo.MONTANT_FIXE, 2_000, c -> c.setUtilisationsMax(5));
        VarianteProduit variante = variante(10_000, 10);
        String jeton = panierAvec(variante, 2);
        envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/panier/code-promo"),
                jeton, null, "{\"code\":\"%s\"}".formatted(code));
        String numero = corps(commander(jeton, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 20_000))
                .andExpect(status().isCreated())).path("numero").asString();
        assertThat(stock(variante)).isEqualTo(8);
        assertThat(compter("select nombre_utilisations from code_promo where code = ?", code)).isEqualTo(1);
        var agent = utilisateur(Role.SERVICE_CLIENT);

        admin(numero, "annuler", acces(agent), "{\"motif\":\"Client injoignable\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ANNULEE"));

        assertThat(statut(numero)).isEqualTo("ANNULEE");
        assertThat(stock(variante)).isEqualTo(10);
        assertThat(stockTotalProduit(variante)).isEqualTo(10);
        assertThat(compter("select nombre_utilisations from code_promo where code = ?", code)).isZero();
        assertThat(compter("""
                select count(*) from mouvement_stock m join commande c on c.id = m.commande_id
                 where c.numero = ? and m.type = 'ANNULATION' and m.quantite = 2 and m.stock_apres = 10 and m.auteur = ?
                """, numero, agent.getEmail())).isEqualTo(1);
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.statut = 'ANNULE'", numero)).isEqualTo(1);
        assertThat(compter("""
                select count(*) from historique_statut_commande h join commande c on c.id = h.commande_id
                 where c.numero = ? and h.nouveau_statut = 'ANNULEE' and h.commentaire = 'Client injoignable'
                """, numero)).isEqualTo(1);

        // Pas de seconde libération du stock ni du code promo, et plus de confirmation possible
        admin(numero, "annuler", acces(agent), "{\"motif\":\"Encore\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATUT_INVALIDE"));
        admin(numero, "confirmer", acces(agent), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("annulée")));
        assertThat(stock(variante)).isEqualTo(10);
        assertThat(compter("select nombre_utilisations from code_promo where code = ?", code)).isZero();
    }

    @Test
    void lAnnulationExigeUnMotifEtRefuseUneCommandeDejaPayee() throws Exception {
        VarianteProduit variante = variante(10_000, 10);
        String numero = commandeEnLigne(variante, 12_000).path("numero").asString();
        var agent = utilisateur(Role.SERVICE_CLIENT);

        admin(numero, "annuler", acces(agent), "{\"motif\":\"\"}").andExpect(status().isBadRequest());
        admin(numero, "annuler", acces(agent), "{}").andExpect(status().isBadRequest());

        // Une commande réglée se rembourse (avoir), elle ne s'annule pas ici
        jdbc.sql("update commande set statut = 'PAYEE', expire_le = null where numero = :n").param("n", numero).update();
        admin(numero, "annuler", acces(agent), "{\"motif\":\"Erreur\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATUT_INVALIDE"));
        assertThat(stock(variante)).isEqualTo(9);
    }

    // ------------------------------------------------------------------ expiration

    @Test
    void unPaiementEnLigneNonRegleExpireApresLeDelaiEtLeStockEstLibere() throws Exception {
        String code = code(TypeCodePromo.MONTANT_FIXE, 1_000, c -> c.setUtilisationsMax(5));
        VarianteProduit variante = variante(10_000, 10);
        String jeton = panierAvec(variante, 1);
        envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/panier/code-promo"),
                jeton, null, "{\"code\":\"%s\"}".formatted(code));
        String numero = corps(commander(jeton, null, cle(), requete(telephone(), "MOBILE_MONEY", 11_000))
                .andExpect(status().isCreated())).path("numero").asString();
        assertThat(stock(variante)).isEqualTo(9);

        horloge.avancer(Duration.ofMinutes(29));
        assertThat(expiration.expirer()).isZero();
        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");

        horloge.avancer(Duration.ofMinutes(2));
        assertThat(expiration.expirer()).isEqualTo(1);

        assertThat(statut(numero)).isEqualTo("ANNULEE");
        assertThat(stock(variante)).isEqualTo(10);
        assertThat(compter("select nombre_utilisations from code_promo where code = ?", code)).isZero();
        assertThat(compter("""
                select count(*) from historique_statut_commande h join commande c on c.id = h.commande_id
                 where c.numero = ? and h.nouveau_statut = 'ANNULEE' and h.auteur = 'SYSTEME'
                   and h.commentaire like 'Paiement non reçu%'
                """, numero)).isEqualTo(1);
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.statut = 'ANNULE'", numero)).isEqualTo(1);
        // Un second passage ne libère rien de plus
        assertThat(expiration.expirer()).isZero();
        assertThat(stock(variante)).isEqualTo(10);
    }

    @Test
    void uneCommandeALaLivraisonNonConfirmeeExpireApres24Heures() throws Exception {
        VarianteProduit variante = variante(10_000, 10);
        String numero = commandeALaLivraison(variante, 3, 32_000).path("numero").asString();
        assertThat(stock(variante)).isEqualTo(7);

        horloge.avancer(Duration.ofHours(23));
        assertThat(expiration.expirer()).isZero();
        horloge.avancer(Duration.ofHours(2));
        assertThat(expiration.expirer()).isEqualTo(1);

        assertThat(statut(numero)).isEqualTo("ANNULEE");
        assertThat(stock(variante)).isEqualTo(10);
        assertThat(compter("""
                select count(*) from historique_statut_commande h join commande c on c.id = h.commande_id
                 where c.numero = ? and h.nouveau_statut = 'ANNULEE' and h.commentaire like 'Commande non confirmée%'
                """, numero)).isEqualTo(1);
    }

    @Test
    void lesDelaisDExpirationSontReglables() throws Exception {
        jdbc.sql("update parametres_site set delai_paiement_mobile_minutes = 10, "
                + "paiement_livraison_delai_annulation_heures = 2 where id = 1").update();
        VarianteProduit variante = variante(10_000, 10);
        String enLigne = commandeEnLigne(variante, 12_000).path("numero").asString();
        String livraison = commandeALaLivraison(variante, 1, 12_000).path("numero").asString();

        horloge.avancer(Duration.ofMinutes(11));
        assertThat(expiration.expirer()).isEqualTo(1);
        assertThat(statut(enLigne)).isEqualTo("ANNULEE");
        assertThat(statut(livraison)).isEqualTo("EN_ATTENTE_CONFIRMATION");
        horloge.avancer(Duration.ofHours(2));
        assertThat(expiration.expirer()).isEqualTo(1);
        assertThat(statut(livraison)).isEqualTo("ANNULEE");
        assertThat(stock(variante)).isEqualTo(10);
    }

    @Test
    void deuxPassagesSimultanesDeLExpirationNeLiberentLeStockQuUneFois() throws Exception {
        VarianteProduit variante = variante(10_000, 10);
        for (int i = 0; i < 3; i++) {
            commandeALaLivraison(variante, 1, 12_000);
        }
        assertThat(stock(variante)).isEqualTo(7);
        horloge.avancer(Duration.ofHours(25));

        ExecutorService executeur = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<Integer>> passages = List.of(
                    executeur.submit(() -> { depart.await(); return expiration.expirer(); }),
                    executeur.submit(() -> { depart.await(); return expiration.expirer(); }));
            depart.countDown();
            int annulees = 0;
            for (Future<Integer> passage : passages) {
                annulees += passage.get();
            }
            assertThat(annulees).isEqualTo(3);
        } finally {
            executeur.shutdownNow();
        }
        assertThat(stock(variante)).isEqualTo(10);
        assertThat(compter("select count(*) from mouvement_stock where variante_id = ? and type = 'ANNULATION'",
                variante.getId())).isEqualTo(3);
    }

    @Test
    void laPurgeSupprimeLesClesDIdempotencePerimeesSeulement() throws Exception {
        String ancienne = cle();
        String jeton = panierAvec(variante(10_000, 5), 1);
        commander(jeton, null, ancienne, requete(telephone(), "A_LA_LIVRAISON", 12_000)).andExpect(status().isCreated());
        horloge.avancer(Duration.ofHours(25));
        String recente = cle();
        String jeton2 = panierAvec(variante(10_000, 5), 1);
        commander(jeton2, null, recente, requete(telephone(), "A_LA_LIVRAISON", 12_000)).andExpect(status().isCreated());

        expiration.purger();

        assertThat(compter("select count(*) from cle_idempotence where cle = ?", ancienne)).isZero();
        assertThat(compter("select count(*) from cle_idempotence where cle = ?", recente)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ suivi

    @Test
    void laPageDeSuiviNeMontreQueDesDonneesMasquees() throws Exception {
        VarianteProduit variante = variante(10_000, 10);
        JsonNode creee = commandeALaLivraison(variante, 2, 22_000);
        String suivi = creee.path("jetonSuivi").asString();

        String contenu = mockMvc.perform(get("/api/commandes/suivi/{jeton}", suivi))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.numero").value(creee.path("numero").asString()))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_CONFIRMATION"))
                .andExpect(jsonPath("$.prenomClient").value("Awa"))
                .andExpect(jsonPath("$.telephoneMasque").value(org.hamcrest.Matchers.matchesPattern(
                        "\\+221 78 ••• •• \\d{2}")))
                .andExpect(jsonPath("$.adresseMasquee").value(org.hamcrest.Matchers.startsWith("Médina, Dakar")))
                .andExpect(jsonPath("$.total").value(22_000))
                .andExpect(jsonPath("$.lignes[0].quantite").value(2))
                .andExpect(jsonPath("$.etapes[0].statut").value("EN_ATTENTE_CONFIRMATION"))
                .andExpect(jsonPath("$.peutReprendrePaiement").value(false))
                .andReturn().getResponse().getContentAsString();

        // Rien d'inutile : ni e-mail, ni téléphone complet, ni rue, ni point de repère, ni IP, ni auteur
        assertThat(contenu).doesNotContain("awa@exemple.sn", "Rue 10 x 11", "Près de la mosquée", "127.0.0.1",
                "jetonSuivi", "SYSTEME", "adresseIp", "userAgent", "utm");
        assertThat(contenu).doesNotContainPattern("\\+22178\\d{7}");
    }

    @Test
    void unJetonDeSuiviInconnuOuMalFormeDonne404() throws Exception {
        mockMvc.perform(get("/api/commandes/suivi/{jeton}", "a".repeat(43))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/commandes/suivi/{jeton}", "court")).andExpect(status().isNotFound());
        envoyer(post("/api/commandes/suivi/{jeton}/reprise-paiement", "a".repeat(43)), null, null, null)
                .andExpect(status().isNotFound());
        envoyer(post("/api/commandes/suivi/{jeton}/achat-suivi", "a".repeat(43)), null, null, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void lePaiementSeRepreendDepuisLeSuiviTantQueLaCommandeNAPasExpire() throws Exception {
        VarianteProduit variante = variante(10_000, 10);
        JsonNode creee = commandeEnLigne(variante, 12_000);
        String suivi = creee.path("jetonSuivi").asString();

        mockMvc.perform(get("/api/commandes/suivi/{jeton}", suivi))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_PAIEMENT"))
                .andExpect(jsonPath("$.peutReprendrePaiement").value(true))
                .andExpect(jsonPath("$.expireLe").exists());

        // Une commande payée à la livraison n'a pas de paiement à reprendre
        String livraison = commandeALaLivraison(variante, 1, 12_000).path("jetonSuivi").asString();
        envoyer(post("/api/commandes/suivi/{jeton}/reprise-paiement", livraison), null, null, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAIEMENT_NON_REPRENABLE"));

        // Après l'échéance : plus de reprise, même avant le passage de la tâche d'expiration
        horloge.avancer(Duration.ofMinutes(31));
        mockMvc.perform(get("/api/commandes/suivi/{jeton}", suivi))
                .andExpect(jsonPath("$.peutReprendrePaiement").value(false));
        envoyer(post("/api/commandes/suivi/{jeton}/reprise-paiement", suivi), null, null, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMANDE_EXPIREE"));
        expiration.expirer();
        mockMvc.perform(get("/api/commandes/suivi/{jeton}", suivi)).andExpect(jsonPath("$.statut").value("ANNULEE"));
        envoyer(post("/api/commandes/suivi/{jeton}/reprise-paiement", suivi), null, null, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAIEMENT_NON_REPRENABLE"));
    }

    @Test
    void lEvenementAchatPartUneSeuleFoisEtSeulementQuandLaVenteEstAcquise() throws Exception {
        VarianteProduit variante = variante(10_000, 10);
        JsonNode creee = commandeALaLivraison(variante, 1, 12_000);
        String suivi = creee.path("jetonSuivi").asString();
        String numero = creee.path("numero").asString();
        var agent = utilisateur(Role.SERVICE_CLIENT);

        // Commande à la livraison non confirmée : pas encore une vente
        mockMvc.perform(get("/api/commandes/suivi/{jeton}", suivi)).andExpect(jsonPath("$.achatASuivre").value(false));
        envoyer(post("/api/commandes/suivi/{jeton}/achat-suivi", suivi), null, null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.emettre").value(false));

        admin(numero, "confirmer", acces(agent), null).andExpect(status().isOk());
        mockMvc.perform(get("/api/commandes/suivi/{jeton}", suivi)).andExpect(jsonPath("$.achatASuivre").value(true));
        // Deux onglets en même temps : un seul émet
        envoyer(post("/api/commandes/suivi/{jeton}/achat-suivi", suivi), null, null, null)
                .andExpect(jsonPath("$.emettre").value(true));
        envoyer(post("/api/commandes/suivi/{jeton}/achat-suivi", suivi), null, null, null)
                .andExpect(jsonPath("$.emettre").value(false));
        mockMvc.perform(get("/api/commandes/suivi/{jeton}", suivi)).andExpect(jsonPath("$.achatASuivre").value(false));
    }

    @Test
    void lesRoutesDeSuiviQuiModifientExigentOrigin() throws Exception {
        String suivi = commandeALaLivraison(variante(10_000, 10), 1, 12_000).path("jetonSuivi").asString();

        mockMvc.perform(post("/api/commandes/suivi/{jeton}/achat-suivi", suivi)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/commandes/suivi/{jeton}/reprise-paiement", suivi)).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ notifications

    @Test
    void leLienDeSuiviEstEnvoyeParEmailEtParNotificationApresLeCommit() throws Exception {
        VarianteProduit variante = variante(10_000, 10);
        String email = "client-" + COMPTEUR.incrementAndGet() + "@exemple.sn";
        String tel = telephone();
        String jeton = panierAvec(variante, 1);
        JsonNode creee = corps(commander(jeton, null, cle(), requete(tel, "A_LA_LIVRAISON", 12_000, "Awa Diop", email))
                .andExpect(status().isCreated()));
        String lien = proprietes.urlSiteSansBarre() + "/suivi/" + creee.path("jetonSuivi").asString();

        // Écouteur asynchrone déclenché après le commit
        assertThat(attendreEnvoi("+221" + tel).message()).contains(creee.path("numero").asString(), lien);
        ClientMailpit mailpit = new ClientMailpit(urlMailpit);
        ClientMailpit.Email recu = mailpit.attendre(email, 1).getFirst();
        assertThat(recu.sujet()).isEqualTo("Commande " + creee.path("numero").asString() + " reçue");
        assertThat(recu.html()).contains(creee.path("numero").asString(), lien, "confirmer votre commande");
    }

    @Test
    void sansEmailSeulLaNotificationPart() throws Exception {
        String jeton = panierAvec(variante(10_000, 10), 1);
        String tel = telephone();
        JsonNode creee = corps(commander(jeton, null, cle(),
                requete(tel, "A_LA_LIVRAISON", 12_000, "Moussa", null)).andExpect(status().isCreated()));

        assertThat(attendreEnvoi("+221" + tel).message()).contains(creee.path("jetonSuivi").asString());
        // Appel direct : aucune erreur sans adresse, la commande est inchangée
        long id = jdbc.sql("select id from commande where numero = :n").param("n", creee.path("numero").asString())
                .query(Long.class).single();
        notificationsCommande.envoyerCommandeRecue(id, creee.path("jetonSuivi").asString());
        assertThat(statut(creee.path("numero").asString())).isEqualTo("EN_ATTENTE_CONFIRMATION");
    }

    /** Attend la notification de ce numéro (les écouteurs asynchrones d'autres tests peuvent arriver en retard). */
    private FauxNotificationClient.Envoi attendreEnvoi(String telephone) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            var trouve = notifications.envois().stream().filter(e -> e.telephone().equals(telephone)).findFirst();
            if (trouve.isPresent()) {
                return trouve.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Aucune notification envoyée à " + telephone);
    }
}
