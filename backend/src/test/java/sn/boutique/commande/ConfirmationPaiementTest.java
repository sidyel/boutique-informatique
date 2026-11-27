package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.paiement.ConfirmationPaiementService;
import sn.boutique.commande.paiement.ConfirmationPaiementService.Resultat;
import sn.boutique.commande.paiement.FournisseurPaiement.Statut;
import sn.boutique.commande.paiement.PaiementIndisponibleException;
import sn.boutique.commande.service.ExpirationCommandes;
import tools.jackson.databind.JsonNode;

/**
 * Vérification d'un paiement auprès du prestataire (hors transaction), paiement tardif après expiration, et
 * expiration qui interroge d'abord le prestataire.
 */
class ConfirmationPaiementTest extends CommandeTestBase {

    private static final AtomicLong COMPTEUR = new AtomicLong(System.nanoTime() % 1_000_000 + 5_000_000);

    @Autowired
    ConfirmationPaiementService confirmation;
    @Autowired
    ExpirationCommandes expiration;

    private static String telephone() {
        return "77%07d".formatted(COMPTEUR.incrementAndGet() % 10_000_000);
    }

    private JsonNode commandeEnLigne(VarianteProduit variante, int quantite, long total) throws Exception {
        String jeton = panierAvec(variante, quantite);
        return corps(commander(jeton, null, cle(), requete(telephone(), "MOBILE_MONEY", total))
                .andExpect(status().isCreated()));
    }

    private long paiementId(String numero) {
        return jdbc.sql("select p.id from paiement p join commande c on c.id = p.commande_id where c.numero = :n "
                + "order by p.id desc limit 1").param("n", numero).query(Long.class).single();
    }

    private String reference(String numero) {
        return jdbc.sql("select p.reference_externe from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = :n order by p.id desc limit 1").param("n", numero).query(String.class).single();
    }

    @Test
    void unPaiementConfirmeParLePrestataireRegleLaCommandeSansTransactionPendantLAppel() throws Exception {
        VarianteProduit variante = variante(10_000, 10);
        String numero = commandeEnLigne(variante, 1, 12_000).path("numero").asString();
        fournisseur.repondreALaVerification(reference(numero), Statut.REUSSI);

        assertThat(confirmation.confirmer(paiementId(numero))).isEqualTo(Resultat.PAYE);

        assertThat(statut(numero)).isEqualTo("PAYEE");
        assertThat(fournisseur.verifications()).hasSize(1);
        assertThat(fournisseur.appelsEnTransaction()).isZero();
    }

    @Test
    void unPaiementNonEncaisseLaisseLaCommandeEnAttente() throws Exception {
        String numero = commandeEnLigne(variante(10_000, 10), 1, 12_000).path("numero").asString();

        assertThat(confirmation.confirmer(paiementId(numero))).isEqualTo(Resultat.EN_ATTENTE);

        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");
    }

    @Test
    void unEchecChezLePrestataireMarqueLeFactureEnEchecSansToucherLaCommande() throws Exception {
        String numero = commandeEnLigne(variante(10_000, 10), 1, 12_000).path("numero").asString();
        fournisseur.repondreALaVerification(reference(numero), Statut.ECHOUE);

        assertThat(confirmation.confirmer(paiementId(numero))).isEqualTo(Resultat.ECHEC);

        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.statut = 'ECHOUE'", numero)).isEqualTo(1);
    }

    @Test
    void unMontantQuiNeCorrespondPasNEstJamaisEnregistre() throws Exception {
        String numero = commandeEnLigne(variante(10_000, 10), 1, 12_000).path("numero").asString();
        String reference = reference(numero);
        fournisseur.repondreALaVerification(reference, Statut.REUSSI);
        fournisseur.annoncerMontant(reference, 500);

        assertThat(confirmation.confirmer(paiementId(numero))).isEqualTo(Resultat.INCOHERENT);

        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.statut = 'REUSSI'", numero)).isZero();
    }

    @Test
    void unPrestataireInjoignableNeModifieRien() throws Exception {
        String numero = commandeEnLigne(variante(10_000, 10), 1, 12_000).path("numero").asString();
        fournisseur.verificationEnPanne(true);

        assertThatThrownBy(() -> confirmation.confirmer(paiementId(numero)))
                .isInstanceOf(PaiementIndisponibleException.class);

        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");
    }

    // ------------------------------------------------------------------ paiement tardif

    @Test
    void unPaiementTardifReactiveLaCommandeSiLeStockLePermet() throws Exception {
        VarianteProduit variante = variante(10_000, 5);
        String numero = commandeEnLigne(variante, 2, 22_000).path("numero").asString();
        assertThat(stock(variante)).isEqualTo(3);
        horloge.avancer(Duration.ofMinutes(31));
        // Le prestataire a été interrogé avant l'annulation : il n'avait pas encore encaissé
        assertThat(expiration.expirer()).isEqualTo(1);
        assertThat(statut(numero)).isEqualTo("ANNULEE");
        assertThat(stock(variante)).isEqualTo(5);

        fournisseur.repondreALaVerification(reference(numero), Statut.REUSSI);
        assertThat(confirmation.confirmer(paiementId(numero))).isEqualTo(Resultat.PAYE);

        assertThat(statut(numero)).isEqualTo("PAYEE");
        assertThat(stock(variante)).as("stock réservé de nouveau").isEqualTo(3);
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.statut = 'REUSSI'", numero)).isEqualTo(1);

    }

    @Test
    void unPaiementTardifSansStockEstARembourserLeClientEstInformeEtLaPageDeSuiviLeDit() throws Exception {
        VarianteProduit variante = variante(10_000, 2);
        JsonNode creee = commandeEnLigne(variante, 2, 22_000);
        String numero = creee.path("numero").asString();
        horloge.avancer(Duration.ofMinutes(31));
        assertThat(expiration.expirer()).isEqualTo(1);
        // Un autre client achète le stock libéré entre-temps
        jdbc.sql("update variante_produit set stock = 0 where id = :id").param("id", variante.getId()).update();

        fournisseur.repondreALaVerification(reference(numero), Statut.REUSSI);
        assertThat(confirmation.confirmer(paiementId(numero))).isEqualTo(Resultat.PAYE);

        assertThat(statut(numero)).isEqualTo("ANNULEE");
        assertThat(stock(variante)).isZero();
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.statut = 'A_REMBOURSER'", numero)).isEqualTo(1);
        // Le client en est informé par message
        // (envoi asynchrone après le commit : attente bornée)
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < limite && notifications.envois().stream()
                .noneMatch(e -> e.message().contains("sera remboursé") && e.message().contains(numero))) {
            Thread.sleep(50);
        }
        assertThat(notifications.envois()).anyMatch(e -> e.message().contains("sera remboursé") && e.message().contains(numero));
        // Et la page de suivi l'affiche
        String suivi = creee.path("jetonSuivi").asString();
        mockMvc.perform(get("/api/commandes/suivi/{jeton}", suivi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remboursementEnCours").value(true));
    }

    // ------------------------------------------------------------------ expiration

    @Test
    void lExpirationVerifieLePaiementAvantDAnnulerEtLaCommandeEstReglee() throws Exception {
        VarianteProduit variante = variante(10_000, 5);
        String numero = commandeEnLigne(variante, 1, 12_000).path("numero").asString();
        // Notification perdue : le client a payé, mais la commande n'a pas été marquée payée
        fournisseur.repondreALaVerification(reference(numero), Statut.REUSSI);
        horloge.avancer(Duration.ofMinutes(31));

        assertThat(expiration.expirer()).isZero();

        assertThat(statut(numero)).isEqualTo("PAYEE");
        assertThat(stock(variante)).isEqualTo(4);
        assertThat(fournisseur.appelsEnTransaction()).isZero();
    }

    @Test
    void unPrestataireInjoignableReporteL_annulationJusqu_ALaFinDuDelaiDeGrace() throws Exception {
        VarianteProduit variante = variante(10_000, 5);
        String numero = commandeEnLigne(variante, 1, 12_000).path("numero").asString();
        fournisseur.verificationEnPanne(true);
        horloge.avancer(Duration.ofMinutes(31));

        assertThat(expiration.expirer()).isZero();
        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");
        assertThat(stock(variante)).isEqualTo(4);

        // Délai de grâce de 2 h écoulé : on annule quand même
        horloge.avancer(Duration.ofHours(2));
        assertThat(expiration.expirer()).isEqualTo(1);
        assertThat(statut(numero)).isEqualTo("ANNULEE");
        assertThat(stock(variante)).isEqualTo(5);
    }

    // ------------------------------------------------------------------ page de retour

    private String jetonRetour() {
        String url = fournisseur.demandes().getFirst().urlRetour();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    @Test
    void laPageDeRetourVerifieAupresDuPrestataireEtNeCroitJamaisLeNavigateur() throws Exception {
        String numero = commandeEnLigne(variante(10_000, 10), 1, 12_000).path("numero").asString();
        String jeton = jetonRetour();

        // Le client revient mais le prestataire n'a pas encaissé : « en attente », même avec ?statut=PAYE dans l'adresse
        mockMvc.perform(get("/api/commandes/retour/{jeton}", jeton).param("statut", "PAYE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.numero").value(numero));
        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");

        fournisseur.repondreALaVerification(reference(numero), Statut.REUSSI);
        mockMvc.perform(get("/api/commandes/retour/{jeton}", jeton))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("PAYE"));
        assertThat(statut(numero)).isEqualTo("PAYEE");
    }

    @Test
    void laPageDeRetourResteUtilisableSiLePrestataireEstInjoignableEtRefuseUnJetonInconnu() throws Exception {
        commandeEnLigne(variante(10_000, 10), 1, 12_000);
        fournisseur.verificationEnPanne(true);

        mockMvc.perform(get("/api/commandes/retour/{jeton}", jetonRetour()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.verifie").value(false));
        mockMvc.perform(get("/api/commandes/retour/{jeton}", "x".repeat(43))).andExpect(status().isNotFound());
        // Un jeton de suivi n'ouvre pas la page de retour
        mockMvc.perform(get("/api/commandes/retour/{jeton}", "j".repeat(43))).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ mobile money masqué sans clés

    @Autowired
    java.util.List<sn.boutique.commande.paiement.FournisseurPaiement> fournisseurs;

    @Test
    void sansClesPayDunyaLeMobileMoneyEstMasqueEtLePaiementALaLivraisonFonctionne() throws Exception {
        // Le vrai fournisseur est dans le contexte, sans clés (les tests n'en ont jamais) : il n'est pas disponible
        assertThat(fournisseurs).anyMatch(f -> f instanceof sn.boutique.commande.paiement.PaydunyaFournisseur
                && !f.disponible());
        fournisseur.configurer(false);

        mockMvc.perform(get("/api/commandes/options"))
                .andExpect(jsonPath("$.paiementEnLigne").value(false))
                .andExpect(jsonPath("$.paiementLivraison").value(true));
        String jeton = panierAvec(variante(10_000, 5), 1);
        commander(jeton, null, cle(), requete(telephone(), "MOBILE_MONEY", 12_000))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MODE_PAIEMENT_INDISPONIBLE"));
        commander(jeton, null, cle(), requete(telephone(), "A_LA_LIVRAISON", 12_000)).andExpect(status().isCreated());
    }
}
