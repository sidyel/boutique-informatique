package sn.boutique.commande.facture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.TestcontainersConfiguration;
import sn.boutique.support.HorlogeReglable;

/**
 * Numérotation continue des pièces (compteur verrouillé, pas de séquence) et immuabilité garantie par la base.
 * Les compteurs de l'année du test sont lus et mesurés au moment du test, jamais supposés (règle 11).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, HorlogeReglable.Configuration.class})
class NumerotationEtImmuabiliteTest {

    @Autowired
    CompteurDocumentService compteur;
    @Autowired
    TransactionTemplate transaction;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    HorlogeReglable horloge;

    private int annee;
    /** Les commandes insérées par un test, retirées en fin de test avec leurs pièces. */
    private final List<Long> commandesInserees = new ArrayList<>();

    @BeforeEach
    void lireAnnee() {
        annee = Year.now(horloge.withZone(ZoneOffset.UTC)).getValue();
    }

    @AfterEach
    void nettoyer() {
        // Le déclencheur interdit UPDATE et DELETE : le nettoyage le contourne le temps d'une transaction
        // (session_replication_role = replica), réservé aux tests.
        if (commandesInserees.isEmpty()) {
            return;
        }
        transaction.executeWithoutResult(statut -> {
            jdbc.sql("set local session_replication_role = replica").update();
            for (Long id : commandesInserees) {
                jdbc.sql("delete from ligne_avoir where avoir_id in (select a.id from avoir a join facture f "
                        + "on f.id = a.facture_id where f.commande_id = :id)").param("id", id).update();
                jdbc.sql("delete from avoir where facture_id in (select id from facture where commande_id = :id)")
                        .param("id", id).update();
                jdbc.sql("delete from ligne_facture where facture_id in (select id from facture "
                        + "where commande_id = :id)").param("id", id).update();
                jdbc.sql("delete from facture where commande_id = :id").param("id", id).update();
                jdbc.sql("delete from commande where id = :id").param("id", id).update();
            }
        });
        commandesInserees.clear();
    }

    private long dernier(TypeDocument type) {
        return jdbc.sql("select coalesce(max(dernier_numero), 0) from compteur_document where type = :t and annee = :a")
                .param("t", type.prefixe()).param("a", annee).query(Long.class).single();
    }

    @Test
    void lesNumerosSontContinusEtFormatesParTypeEtParAnnee() {
        long avantFacture = dernier(TypeDocument.FACTURE);
        long avantAvoir = dernier(TypeDocument.AVOIR);

        List<String> numeros = transaction.execute(statut -> List.of(
                compteur.prochainNumero(TypeDocument.FACTURE),
                compteur.prochainNumero(TypeDocument.FACTURE),
                compteur.prochainNumero(TypeDocument.AVOIR)));

        assertThat(numeros).containsExactly(
                "FAC-%d-%06d".formatted(annee, avantFacture + 1),
                "FAC-%d-%06d".formatted(annee, avantFacture + 2),
                "AV-%d-%06d".formatted(annee, avantAvoir + 1));
    }

    @Test
    void uneTransactionAnnuleeNeConsommeAucunNumero() {
        long avant = dernier(TypeDocument.FACTURE);

        assertThatThrownBy(() -> transaction.executeWithoutResult(statut -> {
            compteur.prochainNumero(TypeDocument.FACTURE);
            compteur.prochainNumero(TypeDocument.FACTURE);
            throw new IllegalStateException("échec de l'émission");
        })).hasMessageContaining("échec");

        assertThat(dernier(TypeDocument.FACTURE)).isEqualTo(avant);
        String suivant = transaction.execute(statut -> compteur.prochainNumero(TypeDocument.FACTURE));
        assertThat(suivant).isEqualTo("FAC-%d-%06d".formatted(annee, avant + 1));
    }

    @Test
    void desEmissionsConcurrentesDonnentDesNumerosConsecutifsSansDoublon() throws Exception {
        long avant = dernier(TypeDocument.FACTURE);
        int fils = 8;
        ExecutorService pool = Executors.newFixedThreadPool(fils);
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<String>> futurs = new ArrayList<>();
        try {
            for (int i = 0; i < fils; i++) {
                futurs.add(pool.submit(() -> {
                    depart.await();
                    return transaction.execute(statut -> compteur.prochainNumero(TypeDocument.FACTURE));
                }));
            }
            depart.countDown();
            List<String> numeros = new ArrayList<>();
            for (Future<String> futur : futurs) {
                numeros.add(futur.get());
            }
            Collections.sort(numeros);
            List<String> attendus = new ArrayList<>();
            for (int i = 1; i <= fils; i++) {
                attendus.add("FAC-%d-%06d".formatted(annee, avant + i));
            }
            assertThat(numeros).isEqualTo(attendus);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void lAttributionHorsTransactionEstRefusee() {
        // Un numéro validé tout seul pourrait laisser un trou si l'émission échoue ensuite
        assertThatThrownBy(() -> compteur.prochainNumero(TypeDocument.FACTURE))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    // ------------------------------------------------------------------ immuabilité

    @Test
    void uneFactureEmiseNePeutPlusEtreModifieeNiSupprimee() {
        long commandeId = creerCommande();
        long factureId = insererFacture(commandeId, "FAC-9999-%06d".formatted(commandeId % 1_000_000));

        assertThatThrownBy(() -> jdbc.sql("update facture set client_nom = 'Autre' where id = :id")
                .param("id", factureId).update())
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immuable");
        assertThatThrownBy(() -> jdbc.sql("delete from facture where id = :id").param("id", factureId).update())
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immuable");

        long ligneId = jdbc.sql("""
                insert into ligne_facture (facture_id, rang, type, designation, quantite, prix_unitaire_ttc,
                    total_brut_ttc, remise_ttc, total_ttc, total_ht, montant_tva)
                values (:f, 1, 'ARTICLE', 'Souris', 1, 1000, 1000, 0, 1000, 1000, 0) returning id
                """).param("f", factureId).query(Long.class).single();
        assertThatThrownBy(() -> jdbc.sql("update ligne_facture set quantite = 2 where id = :id")
                .param("id", ligneId).update()).hasMessageContaining("immuable");
        assertThatThrownBy(() -> jdbc.sql("delete from ligne_facture where id = :id").param("id", ligneId).update())
                .hasMessageContaining("immuable");

        // La pièce est intacte
        assertThat(jdbc.sql("select client_nom from facture where id = :id").param("id", factureId)
                .query(String.class).single()).isEqualTo("Awa Diop");
    }

    @Test
    void laBaseRefuseUneFactureIncoherente() {
        long commandeId = creerCommande();
        // HT + TVA différent du TTC
        assertThatThrownBy(() -> jdbc.sql(INSERTION_FACTURE.replace(":total_ht", "900").replace(":total_tva", "50"))
                .param("n", "FAC-9999-000001").param("c", commandeId).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        // TVA non nulle sans être assujetti
        assertThatThrownBy(() -> jdbc.sql(INSERTION_FACTURE.replace(":total_ht", "900").replace(":total_tva", "100"))
                .param("n", "FAC-9999-000002").param("c", commandeId).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        // Numéro mal formé
        assertThatThrownBy(() -> jdbc.sql(INSERTION_FACTURE.replace(":total_ht", "1000").replace(":total_tva", "0"))
                .param("n", "FACTURE-1").param("c", commandeId).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static final String INSERTION_FACTURE = """
            insert into facture (numero, commande_id, date_emission, regime_tva, taux_tva_bp, mention_tva,
                client_nom, client_telephone, mode_paiement, total_ttc, total_ht, total_tva, pdf, pdf_sha256)
            values (:n, :c, now(), 'NON_APPLICABLE', 1800, 'TVA non applicable', 'Awa Diop', '+221781234567',
                'A_LA_LIVRAISON', 1000, :total_ht, :total_tva, '\\x25504446'::bytea, repeat('a', 64))
            returning id
            """;

    private long insererFacture(long commandeId, String numero) {
        return jdbc.sql(INSERTION_FACTURE.replace(":total_ht", "1000").replace(":total_tva", "0"))
                .param("n", numero).param("c", commandeId).query(Long.class).single();
    }

    /** Une commande minimale (colonnes obligatoires seulement), retirée par {@code nettoyer}. */
    private long creerCommande() {
        String numero = "CMD-9999-%06d".formatted(System.nanoTime() % 1_000_000);
        long id = jdbc.sql("""
                insert into commande (numero, email_client, telephone_client, mode_livraison, sous_total,
                    frais_livraison, remise, total, mode_paiement, statut)
                values (:n, 'awa@exemple.sn', '+221781234567', 'RETRAIT_MAGASIN', 1000, 0, 0, 1000,
                    'A_LA_LIVRAISON', 'CONFIRMEE')
                returning id
                """).param("n", numero).query(Long.class).single();
        commandesInserees.add(id);
        return id;
    }
}
