package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.paiement.FournisseurPaiement.Statut;
import sn.boutique.commande.paiement.PaydunyaFournisseur;
import sn.boutique.commande.service.ExpirationCommandes;

/**
 * Notification (IPN) de PayDunya : POST en x-www-form-urlencoded, hash fixe, vérification serveur à serveur
 * systématique. Le vrai {@link PaydunyaFournisseur} est remplacé : le hash est contrôlé par ce faux (sa logique est
 * testée dans {@code PaydunyaFournisseurTest}), la vérification de la facture par le faux prestataire des tests.
 */
class NotificationPaydunyaTest extends CommandeTestBase {

    private static final String HASH_BON = "a".repeat(128);
    private static final AtomicLong COMPTEUR = new AtomicLong(System.nanoTime() % 1_000_000 + 3_000_000);

    @MockitoBean
    PaydunyaFournisseur paydunya;
    @Autowired
    ExpirationCommandes expiration;

    @BeforeEach
    void authentifier() {
        when(paydunya.notificationAuthentique(any())).thenAnswer(a -> HASH_BON.equals(a.getArgument(0)));
    }

    private static String telephone() {
        return "78%07d".formatted(COMPTEUR.incrementAndGet() % 10_000_000);
    }

    private String commande(VarianteProduit variante, int quantite, long total) throws Exception {
        String jeton = panierAvec(variante, quantite);
        return corps(commander(jeton, null, cle(), requete(telephone(), "MOBILE_MONEY", total))
                .andExpect(status().isCreated())).path("numero").asString();
    }

    private String reference(String numero) {
        return jdbc.sql("select p.reference_externe from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = :n order by p.id desc limit 1").param("n", numero).query(String.class).single();
    }

    /** Ce qu'envoie PayDunya : un formulaire dont les champs sont sous la clé « data ». */
    private ResultActions notifier(String hash, String jeton, String statutAnnonce) throws Exception {
        var requete = post("/api/paiements/paydunya/webhook").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("data[status]", statutAnnonce).param("data[invoice][total_amount]", "1");
        if (hash != null) {
            requete.param("data[hash]", hash);
        }
        if (jeton != null) {
            requete.param("data[invoice][token]", jeton);
        }
        return mockMvc.perform(requete);
    }

    @Test
    void unMauvaisHashEstRefuseEtNeDeclencheAucuneVerification() throws Exception {
        String numero = commande(variante(10_000, 5), 1, 12_000);
        String jeton = reference(numero);
        fournisseur.repondreALaVerification(jeton, Statut.REUSSI);

        notifier("b".repeat(128), jeton, "completed").andExpect(status().isUnauthorized());
        notifier(null, jeton, "completed").andExpect(status().isUnauthorized());

        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");
        assertThat(fournisseur.verifications()).isEmpty();
    }

    @Test
    void unBonHashDeclencheLaVerificationEtRegleLaCommandeUneSeuleFois() throws Exception {
        String numero = commande(variante(10_000, 5), 1, 12_000);
        String jeton = reference(numero);
        fournisseur.repondreALaVerification(jeton, Statut.REUSSI);

        notifier(HASH_BON, jeton, "completed").andExpect(status().isOk());
        notifier(HASH_BON, jeton, "completed").andExpect(status().isOk());

        assertThat(statut(numero)).isEqualTo("PAYEE");
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.statut = 'REUSSI'", numero)).isEqualTo(1);
        assertThat(fournisseur.appelsEnTransaction()).isZero();
    }

    @Test
    void unBonHashMaisUneVerificationQuiContreditNeRegleRien() throws Exception {
        String numero = commande(variante(10_000, 5), 1, 12_000);
        String jeton = reference(numero);
        // Le corps annonce « completed », PayDunya répond « pending » à la vérification : on croit la vérification
        notifier(HASH_BON, jeton, "completed").andExpect(status().isOk());
        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");

        // Montant différent chez le prestataire : rien n'est enregistré, l'équipe est alertée
        fournisseur.repondreALaVerification(jeton, Statut.REUSSI);
        fournisseur.annoncerMontant(jeton, 100);
        notifier(HASH_BON, jeton, "completed").andExpect(status().isOk());
        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");

        // Échec annoncé : la facture est en échec, la commande reste en attente (le client peut reprendre)
        fournisseur.repondreALaVerification(jeton, Statut.ECHOUE);
        notifier(HASH_BON, jeton, "failed").andExpect(status().isOk());
        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.statut = 'ECHOUE'", numero)).isEqualTo(1);
    }

    @Test
    void uneFactureInconnueUnJetonAbsentOuUnPrestataireEnPanne() throws Exception {
        String numero = commande(variante(10_000, 5), 1, 12_000);
        String jeton = reference(numero);

        notifier(HASH_BON, "inconnue", "completed").andExpect(status().isOk());
        notifier(HASH_BON, null, "completed").andExpect(status().isBadRequest());
        fournisseur.verificationEnPanne(true);
        notifier(HASH_BON, jeton, "completed").andExpect(status().isServiceUnavailable());

        assertThat(statut(numero)).isEqualTo("EN_ATTENTE_PAIEMENT");
    }

    // ------------------------------------------------------------------ paiement tardif (jusqu'à 24 h)

    @Test
    void unPaiementArriveApresLExpirationEtJusqua24HReactiveLaCommandeSiLeStockLePermet() throws Exception {
        VarianteProduit variante = variante(10_000, 5);
        String numero = commande(variante, 2, 22_000);
        String jeton = reference(numero);
        assertThat(stock(variante)).isEqualTo(3);

        // La commande expire à 30 minutes ; la facture, elle, reste payable chez PayDunya (24 h)
        horloge.avancer(Duration.ofMinutes(31));
        assertThat(expiration.expirer()).isEqualTo(1);
        assertThat(statut(numero)).isEqualTo("ANNULEE");
        assertThat(stock(variante)).isEqualTo(5);

        horloge.avancer(Duration.ofHours(22));
        fournisseur.repondreALaVerification(jeton, Statut.REUSSI);
        notifier(HASH_BON, jeton, "completed").andExpect(status().isOk());

        assertThat(statut(numero)).isEqualTo("PAYEE");
        assertThat(stock(variante)).isEqualTo(3);
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.statut = 'REUSSI'", numero)).isEqualTo(1);
    }

    @Test
    void unPaiementTardifSansStockEstARembourserAvecAlerte() throws Exception {
        VarianteProduit variante = variante(10_000, 2);
        String numero = commande(variante, 2, 22_000);
        String jeton = reference(numero);
        horloge.avancer(Duration.ofMinutes(31));
        assertThat(expiration.expirer()).isEqualTo(1);
        jdbc.sql("update variante_produit set stock = 0 where id = :id").param("id", variante.getId()).update();

        horloge.avancer(Duration.ofHours(20));
        fournisseur.repondreALaVerification(jeton, Statut.REUSSI);
        notifier(HASH_BON, jeton, "completed").andExpect(status().isOk());

        assertThat(statut(numero)).isEqualTo("ANNULEE");
        assertThat(stock(variante)).isZero();
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.statut = 'A_REMBOURSER'", numero)).isEqualTo(1);
    }
}
