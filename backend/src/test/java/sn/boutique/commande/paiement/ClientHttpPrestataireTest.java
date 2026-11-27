package sn.boutique.commande.paiement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Le client HTTP du prestataire de paiement face à un prestataire normal, lent, en panne ou injoignable. Aucun appel
 * réseau réel : un petit serveur local joue le prestataire.
 */
class ClientHttpPrestataireTest {

    private static final ProprietesClientPaiement PROPRIETES = new ProprietesClientPaiement(
            Duration.ofMillis(500), Duration.ofMillis(400), 2, Duration.ofMillis(20));

    private HttpServer serveur;
    private final AtomicInteger requetes = new AtomicInteger();
    private volatile IntFunction<Integer> statutDe = numero -> 200;
    private volatile long delaiMs;
    private String base;
    private final ClientHttpPrestataire client = new ClientHttpPrestataire(PROPRIETES);

    @BeforeEach
    void demarrerServeur() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/", echange -> {
            int numero = requetes.incrementAndGet();
            try {
                if (delaiMs > 0) {
                    Thread.sleep(delaiMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] corps = "{\"ok\":true}".getBytes();
            try {
                echange.sendResponseHeaders(statutDe.apply(numero), corps.length);
                echange.getResponseBody().write(corps);
            } catch (IOException e) {
                // le client a abandonné (délai dépassé) : normal dans ces tests
            } finally {
                echange.close();
            }
        });
        // Plusieurs requêtes à la fois (le prestataire lent ne doit pas en retenir d'autres)
        serveur.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        serveur.start();
        base = "http://127.0.0.1:" + serveur.getAddress().getPort();
    }

    @AfterEach
    void arreterServeur() {
        serveur.stop(0);
    }

    @Test
    void uneReponseNormaleEstRenvoyee() {
        var reponse = client.creer(base + "/creer", Map.of("X-Cle", "valeur"), "{}");

        assertThat(reponse.reussie()).isTrue();
        assertThat(reponse.corps()).isEqualTo("{\"ok\":true}");
        assertThat(requetes.get()).isEqualTo(1);
    }

    @Test
    void laCreationNeEstJamaisReessayeMemeEnCasDeDelaiOuDeErreurSerieuse() {
        delaiMs = 1_500;
        long debut = System.nanoTime();
        assertThatThrownBy(() -> client.creer(base + "/creer", Map.of(), "{}"))
                .isInstanceOf(PaiementIndisponibleException.class);
        long ms = (System.nanoTime() - debut) / 1_000_000;
        // Une seule tentative, abandonnée aux délais prévus (bien avant la réponse du prestataire lent)
        assertThat(ms).isLessThan(1_200);
        assertThat(requetes.get()).isEqualTo(1);

        delaiMs = 0;
        statutDe = numero -> 503;
        requetes.set(0);
        var reponse = client.creer(base + "/creer", Map.of(), "{}");
        assertThat(reponse.statut()).isEqualTo(503);
        assertThat(requetes.get()).as("aucun réessai à la création").isEqualTo(1);
    }

    @Test
    void laLectureReessaieUnNombreLimiteDeFoisSurUneErreurDuPrestataire() {
        statutDe = numero -> numero < 3 ? 503 : 200;

        var reponse = client.lire(base + "/confirmer/abc", Map.of());

        assertThat(reponse.reussie()).isTrue();
        assertThat(requetes.get()).isEqualTo(3);
    }

    @Test
    void laLectureAbandonneApresLeNombreDeReessaisPrevu() {
        statutDe = numero -> 502;

        assertThatThrownBy(() -> client.lire(base + "/confirmer/abc", Map.of()))
                .isInstanceOf(PaiementIndisponibleException.class);
        assertThat(requetes.get()).as("1 essai + 2 réessais").isEqualTo(3);
    }

    @Test
    void uneErreurDuClientNEstJamaisReessayee() {
        statutDe = numero -> 404;

        var reponse = client.lire(base + "/confirmer/inconnu", Map.of());

        assertThat(reponse.statut()).isEqualTo(404);
        assertThat(requetes.get()).isEqualTo(1);
    }

    @Test
    void unPrestataireLentFaitEchouerLaLectureAuxDelaisPrevus() {
        delaiMs = 3_000;
        long debut = System.nanoTime();

        assertThatThrownBy(() -> client.lire(base + "/confirmer/abc", Map.of()))
                .isInstanceOf(PaiementIndisponibleException.class);

        long ms = (System.nanoTime() - debut) / 1_000_000;
        // 3 essais de ~400 ms au plus, pas les 3 s de réponse du prestataire par essai
        assertThat(ms).isLessThan(3_000);
        assertThat(requetes.get()).isEqualTo(3);
    }

    @Test
    void unPrestataireInjoignableEchoueVite() throws IOException {
        int portFerme;
        try (ServerSocket socket = new ServerSocket(0)) {
            portFerme = socket.getLocalPort();
        }
        long debut = System.nanoTime();

        assertThatThrownBy(() -> client.creer("http://127.0.0.1:" + portFerme + "/creer", Map.of(), "{}"))
                .isInstanceOf(PaiementIndisponibleException.class)
                .hasMessageContaining("injoignable");

        assertThat((System.nanoTime() - debut) / 1_000_000).isLessThan(1_000);
    }
}
