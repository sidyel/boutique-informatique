package sn.boutique.commande.paiement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.commande.paiement.FournisseurPaiement.DemandePaiement;
import sn.boutique.commande.paiement.FournisseurPaiement.Statut;

/**
 * Tests de contrat de {@link PaydunyaFournisseur} : des réponses reproduisant celles de la documentation
 * (developers.paydunya.com/doc/FR/http_json), servies par un faux serveur. Aucun appel au vrai PayDunya.
 * Les formats que la documentation ne détaille pas (refus de création, devise, forme de {@code custom_data}) sont
 * marqués {@code SUPPOSE} dans {@link PaydunyaFournisseur} : ces tests fixent notre lecture, à confirmer au premier
 * test réel.
 */
@ExtendWith(OutputCaptureExtension.class)
class PaydunyaFournisseurTest {

    private static final String CLE_MAITRE = "cle-maitre-secrete-test";
    private static final String CLE_PRIVEE = "cle-privee-secrete-test";
    private static final String JETON = "jeton-secret-test";
    private static final String BASE_TEST = "https://app.paydunya.com/sandbox-api/v1/";

    private static final ProprietesClientPaiement HTTP = new ProprietesClientPaiement(
            Duration.ofSeconds(3), Duration.ofSeconds(10), 2, Duration.ofMillis(10));

    private MockRestServiceServer serveur;
    private PaydunyaFournisseur fournisseur;

    @BeforeEach
    void preparer() {
        RestClient.Builder constructeur = RestClient.builder();
        serveur = MockRestServiceServer.bindTo(constructeur).build();
        fournisseur = new PaydunyaFournisseur(
                new ProprietesPaydunya(CLE_MAITRE, CLE_PRIVEE, JETON, "test", "Boutique Test"),
                new ClientHttpPrestataire(HTTP, constructeur));
    }

    private static DemandePaiement demande() {
        return new DemandePaiement("CMD-2026-000042", 125_000, ModePaiement.MOBILE_MONEY, "Awa Diop", "771234567",
                null, List.of(new FournisseurPaiement.Article("Souris", 1, 125_000)),
                "https://boutique.sn/commande/retour/abc", "https://boutique.sn/commande/retour/abc?annule=1",
                "https://boutique.sn/api/paiements/paydunya/webhook");
    }

    // ------------------------------------------------------------------ disponibilité

    @Test
    void sansLesTroisClesLeMobileMoneyEstMasque() {
        for (ProprietesPaydunya p : List.of(
                new ProprietesPaydunya("", "", "", "test", "B"),
                new ProprietesPaydunya(null, null, null, "test", "B"),
                new ProprietesPaydunya(CLE_MAITRE, CLE_PRIVEE, " ", "test", "B"),
                new ProprietesPaydunya(CLE_MAITRE, "", JETON, "test", "B"))) {
            assertThat(new PaydunyaFournisseur(p, new ClientHttpPrestataire(HTTP, RestClient.builder())).disponible())
                    .isFalse();
        }
        assertThat(fournisseur.disponible()).isTrue();
    }

    @Test
    void lesClesNeSortentJamaisParLeToStringDesProprietes() {
        assertThat(new ProprietesPaydunya(CLE_MAITRE, CLE_PRIVEE, JETON, "test", "B").toString())
                .doesNotContain(CLE_MAITRE, CLE_PRIVEE, JETON);
    }

    // ------------------------------------------------------------------ création de facture

    @Test
    void creationReussie() {
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PAYDUNYA-MASTER-KEY", CLE_MAITRE))
                .andExpect(header("PAYDUNYA-PRIVATE-KEY", CLE_PRIVEE))
                .andExpect(header("PAYDUNYA-TOKEN", JETON))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.invoice.total_amount").value(125_000))
                .andExpect(jsonPath("$.store.name").value("Boutique Test"))
                .andExpect(jsonPath("$.custom_data.numero_commande").value("CMD-2026-000042"))
                .andExpect(jsonPath("$.actions.return_url").value("https://boutique.sn/commande/retour/abc"))
                .andExpect(jsonPath("$.actions.cancel_url").value("https://boutique.sn/commande/retour/abc?annule=1"))
                .andExpect(jsonPath("$.actions.callback_url").value("https://boutique.sn/api/paiements/paydunya/webhook"))
                // Option A : le client choisit son moyen de paiement sur la page PayDunya, aucun canal imposé
                .andExpect(jsonPath("$.invoice.channels").doesNotExist())
                .andExpect(jsonPath("$.channels").doesNotExist())
                // Aucune donnée personnelle du client n'est envoyée
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("771234567"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Awa"))))
                .andRespond(withSuccess("""
                        {"response_code":"00","response_text":"https://paydunya.com/sandbox-checkout/invoice/test_abc123",
                         "description":"Invoice Created Successfully","token":"test_abc123"}
                        """, MediaType.APPLICATION_JSON));

        var session = fournisseur.initier(demande());

        assertThat(session.referenceExterne()).isEqualTo("test_abc123");
        assertThat(session.urlPaiement()).isEqualTo("https://paydunya.com/sandbox-checkout/invoice/test_abc123");
        serveur.verify();
    }

    @Test
    void enProductionLAdresseEstCelleDeProduction() {
        RestClient.Builder constructeur = RestClient.builder();
        MockRestServiceServer faux = MockRestServiceServer.bindTo(constructeur).build();
        var production = new PaydunyaFournisseur(
                new ProprietesPaydunya(CLE_MAITRE, CLE_PRIVEE, JETON, "production", "B"),
                new ClientHttpPrestataire(HTTP, constructeur));
        faux.expect(once(), requestTo("https://app.paydunya.com/api/v1/checkout-invoice/create"))
                .andRespond(withSuccess("{\"response_code\":\"00\",\"response_text\":\"https://paydunya.com/x\",\"token\":\"t\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(production.initier(demande()).referenceExterne()).isEqualTo("t");
        faux.verify();
    }

    @Test
    void creationRefuseeParUnCodeAutreQue00(CapturedOutput sortie) {
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/create"))
                .andRespond(withSuccess("""
                        {"response_code":"1001","response_text":"Clés invalides","description":"Invalid keys"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fournisseur.initier(demande())).isInstanceOf(PaiementIndisponibleException.class);

        // Le texte du prestataire est journalisé, jamais les clés
        assertThat(sortie.getOut()).contains("response_code=1001", "Clés invalides")
                .doesNotContain(CLE_MAITRE, CLE_PRIVEE, JETON);
        serveur.verify();
    }

    @Test
    void creationRefuseeParUneErreurHttpSansAucunReessai() {
        // Une seule requête attendue : un réessai ferait échouer le faux serveur (risque de facture en double)
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/create"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"response_code\":\"1002\",\"response_text\":\"Unauthorized\"}"));

        assertThatThrownBy(() -> fournisseur.initier(demande())).isInstanceOf(PaiementIndisponibleException.class);
        serveur.verify();

        RestClient.Builder constructeur = RestClient.builder();
        MockRestServiceServer panne = MockRestServiceServer.bindTo(constructeur).build();
        panne.expect(once(), requestTo(BASE_TEST + "checkout-invoice/create")).andRespond(withServerError());
        var autre = new PaydunyaFournisseur(new ProprietesPaydunya(CLE_MAITRE, CLE_PRIVEE, JETON, "test", "B"),
                new ClientHttpPrestataire(HTTP, constructeur));
        assertThatThrownBy(() -> autre.initier(demande())).isInstanceOf(PaiementIndisponibleException.class);
        panne.verify();
    }

    @Test
    void uneReponseIllisibleOuSansAdresseDePaiementEstUnEchec() {
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/create"))
                .andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));
        assertThatThrownBy(() -> fournisseur.initier(demande())).isInstanceOf(PaiementIndisponibleException.class);

        serveur.reset();
        // Succès annoncé mais l'adresse n'est pas en https : jamais de redirection du client vers elle
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/create"))
                .andRespond(withSuccess("{\"response_code\":\"00\",\"response_text\":\"http://exemple.test/x\",\"token\":\"t\"}",
                        MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> fournisseur.initier(demande())).isInstanceOf(PaiementIndisponibleException.class);
    }

    // ------------------------------------------------------------------ vérification (confirm)

    private static String confirm(String statut, String montant) {
        return """
                {"response_code":"00","response_text":"Transaction Found","hash":"abc",
                 "invoice":{"token":"test_abc123","total_amount":%s,"description":"Commande"},
                 "custom_data":{"numero_commande":"CMD-2026-000042"},
                 "mode":"test","status":"%s","fail_reason":null,
                 "customer":{"name":"Awa","phone":"771234567","email":"a@b.sn"},"receipt_url":"https://paydunya.com/r"}
                """.formatted(montant, statut);
    }

    @ParameterizedTest
    @CsvSource({
            "pending,EN_ATTENTE", "PENDING,EN_ATTENTE", "Pending,EN_ATTENTE",
            "completed,REUSSI", "COMPLETED,REUSSI", "Completed,REUSSI",
            "cancelled,ANNULE", "CANCELLED,ANNULE", "canceled,ANNULE",
            "failed,ECHOUE", "FAILED,ECHOUE"})
    void confirmLitLeStatutSansTenirCompteDeLaCasse(String statutPayDunya, Statut attendu) {
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/confirm/test_abc123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("PAYDUNYA-MASTER-KEY", CLE_MAITRE))
                .andExpect(header("PAYDUNYA-PRIVATE-KEY", CLE_PRIVEE))
                .andExpect(header("PAYDUNYA-TOKEN", JETON))
                .andRespond(withSuccess(confirm(statutPayDunya, "125000"), MediaType.APPLICATION_JSON));

        var resultat = fournisseur.verifier("test_abc123");

        assertThat(resultat.statut()).isEqualTo(attendu);
        assertThat(resultat.montant()).isEqualTo(125_000);
        assertThat(resultat.devise()).isEqualTo("XOF");
        assertThat(resultat.numeroCommande()).isEqualTo("CMD-2026-000042");
        serveur.verify();
    }

    @Test
    void leMontantPeutEtreUneChaineOuUnDecimalEntier() {
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/confirm/t1"))
                .andRespond(withSuccess(confirm("completed", "\"125000\""), MediaType.APPLICATION_JSON));
        assertThat(fournisseur.verifier("t1").montant()).isEqualTo(125_000);

        serveur.reset();
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/confirm/t2"))
                .andRespond(withSuccess(confirm("completed", "125000.0"), MediaType.APPLICATION_JSON));
        assertThat(fournisseur.verifier("t2").montant()).isEqualTo(125_000);

        // Montant avec décimales ou absent : -1, la vérification le déclarera incohérent
        serveur.reset();
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/confirm/t3"))
                .andRespond(withSuccess(confirm("completed", "125000.5"), MediaType.APPLICATION_JSON));
        assertThat(fournisseur.verifier("t3").montant()).isEqualTo(-1);
    }

    @Test
    void unStatutInconnuNeDecideRien() {
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/confirm/t"))
                .andRespond(withSuccess(confirm("refunded", "125000"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fournisseur.verifier("t")).isInstanceOf(PaiementIndisponibleException.class);
    }

    @Test
    void confirmReessaieSurErreurServeurPuisReussit() {
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/confirm/t"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/confirm/t"))
                .andRespond(withSuccess(confirm("completed", "125000"), MediaType.APPLICATION_JSON));

        assertThat(fournisseur.verifier("t").statut()).isEqualTo(Statut.REUSSI);
        serveur.verify();
    }

    @Test
    void confirmAbandonneApresLesReessaisPrevus() {
        // 1 essai + 2 réessais = 3 requêtes, pas une de plus
        for (int i = 0; i < 3; i++) {
            serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/confirm/t")).andRespond(withServerError());
        }

        assertThatThrownBy(() -> fournisseur.verifier("t")).isInstanceOf(PaiementIndisponibleException.class);
        serveur.verify();
    }

    @Test
    void uneFactureInconnueNEstPasReessayeNiCrue() {
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/confirm/inconnu"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"response_code\":\"1003\",\"response_text\":\"Not found\"}"));

        assertThatThrownBy(() -> fournisseur.verifier("inconnu")).isInstanceOf(PaiementIndisponibleException.class);
        serveur.verify();
    }

    @Test
    void leJetonDeFactureEstEncodeDansLAdresse() {
        serveur.expect(once(), requestTo(BASE_TEST + "checkout-invoice/confirm/a%2Fb%3Fc"))
                .andRespond(withSuccess(confirm("pending", "125000"), MediaType.APPLICATION_JSON));

        assertThat(fournisseur.verifier("a/b?c").statut()).isEqualTo(Statut.EN_ATTENTE);
        serveur.verify();
    }

    // ------------------------------------------------------------------ authenticité des notifications

    @Test
    void leHashDeNotificationEstLeSha512DeLaCleMaitre() throws Exception {
        String attendu = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-512")
                .digest(CLE_MAITRE.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(fournisseur.notificationAuthentique(attendu)).isTrue();
        assertThat(fournisseur.notificationAuthentique(attendu.toUpperCase())).isTrue();
        assertThat(fournisseur.notificationAuthentique(" " + attendu + " ")).isTrue();
        assertThat(fournisseur.notificationAuthentique(attendu.substring(1))).isFalse();
        assertThat(fournisseur.notificationAuthentique("0".repeat(128))).isFalse();
        assertThat(fournisseur.notificationAuthentique(CLE_MAITRE)).isFalse();
        assertThat(fournisseur.notificationAuthentique("")).isFalse();
        assertThat(fournisseur.notificationAuthentique(null)).isFalse();
    }

    @Test
    void sansCleConfigureeAucuneNotificationNEstAuthentique() throws Exception {
        // Le SHA-512 de la chaîne vide ne doit pas ouvrir la porte quand les clés manquent
        String hashVide = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-512").digest(new byte[0]));
        var nonConfigure = new PaydunyaFournisseur(new ProprietesPaydunya("", "", "", "test", "B"),
                new ClientHttpPrestataire(HTTP, RestClient.builder()));

        assertThat(nonConfigure.notificationAuthentique(hashVide)).isFalse();
    }
}
