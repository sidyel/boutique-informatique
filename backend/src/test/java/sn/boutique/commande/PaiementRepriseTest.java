package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import sn.boutique.commande.paiement.PaiementService;
import sn.boutique.commande.paiement.ReceptionPaiementService;
import sn.boutique.commande.paiement.ReceptionPaiementService.Issue;
import sn.boutique.compte.modele.Role;
import sn.boutique.support.ClientMailpit;
import tools.jackson.databind.JsonNode;

/**
 * Reprise du paiement : une commande n'a jamais deux factures payables, et un paiement reçu en trop est marqué
 * « à rembourser ».
 */
class PaiementRepriseTest extends CommandeTestBase {

    private static final AtomicLong COMPTEUR = new AtomicLong(System.nanoTime() % 1_000_000 + 8_000_000);

    @Autowired
    PaiementService paiementService;
    @Autowired
    ReceptionPaiementService reception;
    @Value("${test.mailpit.url}")
    String urlMailpit;

    private static String telephone() {
        return "76%07d".formatted(COMPTEUR.incrementAndGet() % 10_000_000);
    }

    /** Commande mobile money dont le prestataire n'a pas répondu : aucune facture pour l'instant. */
    private String commandeSansFacture() throws Exception {
        fournisseur.tomberEnPanne(true);
        String jeton = panierAvec(variante(100_000, 5), 1);
        JsonNode reponse = corps(commander(jeton, null, cle(), requete(telephone(), "MOBILE_MONEY", 102_000))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.paiementAReprendre").value(true)));
        fournisseur.tomberEnPanne(false);
        return reponse.path("numero").asString();
    }

    private long factures(String numero, String statut) {
        return compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ? and p.fournisseur = 'PAYDUNYA' and p.statut = ?", numero, statut);
    }

    @Test
    void deuxReprisesSimultaneesNeCreentQuUneSeuleFactureActive() throws Exception {
        String numero = commandeSansFacture();
        fournisseur.repondreApres(400);

        int appels = 4;
        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(appels);
        try {
            List<Future<String>> urls = new ArrayList<>();
            for (int i = 0; i < appels; i++) {
                urls.add(pool.submit(() -> {
                    depart.await();
                    return paiementService.reprendre(numero);
                }));
            }
            depart.countDown();
            List<String> resultats = new ArrayList<>();
            for (Future<String> url : urls) {
                resultats.add(url.get(30, TimeUnit.SECONDS));
            }
            assertThat(resultats).doesNotContainNull().containsOnly(resultats.getFirst());
        } finally {
            pool.shutdownNow();
        }

        assertThat(fournisseur.demandes()).hasSize(1);
        assertThat(factures(numero, "EN_ATTENTE")).isEqualTo(1);
        assertThat(compter("select count(*) from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = ?", numero)).isEqualTo(1);
    }

    @Test
    void uneFactureExpireeEstRemplaceeUneFactureValideEstReutilisee() throws Exception {
        String numero = commandeSansFacture();
        String premiere = paiementService.reprendre(numero);
        assertThat(paiementService.reprendre(numero)).isEqualTo(premiere);

        // Validité de la facture (30 min) dépassée alors que la commande tient encore : nouvelle facture
        jdbc.sql("update commande set expire_le = expire_le + interval '2 hours' where numero = :n")
                .param("n", numero).update();
        horloge.avancer(Duration.ofMinutes(31));
        String seconde = paiementService.reprendre(numero);

        assertThat(seconde).isNotEqualTo(premiere);
        assertThat(fournisseur.demandes()).hasSize(2);
        assertThat(factures(numero, "EN_ATTENTE")).isEqualTo(1);
        assertThat(factures(numero, "ANNULE")).isEqualTo(1);
    }

    @Test
    void uneFactureEchoueeNEstJamaisReutilisee() throws Exception {
        String numero = commandeSansFacture();
        String premiere = paiementService.reprendre(numero);
        jdbc.sql("update paiement set statut = 'ECHOUE' where commande_id = (select id from commande where numero = :n)")
                .param("n", numero).update();

        assertThat(paiementService.reprendre(numero)).isNotEqualTo(premiere);
        assertThat(factures(numero, "ECHOUE")).isEqualTo(1);
        assertThat(factures(numero, "EN_ATTENTE")).isEqualTo(1);
    }

    @Test
    void unSecondPaiementPourUneCommandeDejaReglleEstMarqueARembourserEtSignale() throws Exception {
        String numero = commandeSansFacture();
        paiementService.reprendre(numero);
        // Cas limite : une seconde facture, créée alors que la première était réputée expirée, est payée aussi
        long premier = jdbc.sql("select p.id from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = :n").param("n", numero).query(Long.class).single();
        jdbc.sql("""
                insert into paiement (commande_id, fournisseur, reference_externe, montant, statut, url_paiement)
                select id, 'PAYDUNYA', 'seconde-' || numero, total, 'EN_ATTENTE', 'https://paiement.test/seconde'
                  from commande where numero = :n
                """).param("n", numero).update();
        long second = jdbc.sql("select p.id from paiement p where p.reference_externe = :r")
                .param("r", "seconde-" + numero).query(Long.class).single();

        assertThat(reception.enregistrerPaiementRecu(premier)).isEqualTo(Issue.COMMANDE_REGLEE);
        assertThat(statut(numero)).isEqualTo("PAYEE");
        assertThat(reception.enregistrerPaiementRecu(second)).isEqualTo(Issue.A_REMBOURSER);

        assertThat(statut(numero)).isEqualTo("PAYEE");
        assertThat(factures(numero, "REUSSI")).isEqualTo(1);
        assertThat(factures(numero, "A_REMBOURSER")).isEqualTo(1);
        // Webhook reçu deux fois : rien ne change
        assertThat(reception.enregistrerPaiementRecu(second)).isEqualTo(Issue.DEJA_ENREGISTRE);
        assertThat(reception.enregistrerPaiementRecu(premier)).isEqualTo(Issue.DEJA_ENREGISTRE);

        // Alerte envoyée à l'équipe et paiement visible dans la liste du service financier
        ClientMailpit.Email alerte = new ClientMailpit(urlMailpit).attendre("alertes@boutique.test", 1).getFirst();
        assertThat(alerte.sujet()).contains("Paiement à rembourser", numero);
        String acces = acces(utilisateur(Role.SERVICE_CLIENT));
        mockMvc.perform(get("/api/admin/paiements").header("Authorization", acces))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[?(@.numeroCommande == '" + numero + "')].statut")
                        .value("A_REMBOURSER"));
        mockMvc.perform(get("/api/admin/paiements")).andExpect(status().isUnauthorized());
    }
}
