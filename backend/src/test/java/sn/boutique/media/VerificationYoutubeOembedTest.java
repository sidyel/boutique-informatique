package sn.boutique.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.unit.DataSize;
import sn.boutique.noyau.erreur.RegleMetierException;
import tools.jackson.databind.json.JsonMapper;

/** Implémentation oEmbed réelle, contre un faux serveur HTTP local (aucun appel à YouTube). */
class VerificationYoutubeOembedTest {

    private static final Duration DELAI = Duration.ofMillis(400);

    private HttpServer serveur;
    private final AtomicReference<Integer> statut = new AtomicReference<>(200);
    private final AtomicReference<String> corps = new AtomicReference<>("{}");
    private final AtomicReference<Duration> attente = new AtomicReference<>(Duration.ZERO);
    private final AtomicReference<String> requeteRecue = new AtomicReference<>();
    private VerificationYoutubeOembed verification;

    @BeforeEach
    void demarrer() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/oembed", echange -> {
            requeteRecue.set(echange.getRequestURI().getRawQuery());
            try {
                Thread.sleep(attente.get().toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] octets = corps.get().getBytes(StandardCharsets.UTF_8);
            echange.getResponseHeaders().add("Content-Type", "application/json");
            echange.sendResponseHeaders(statut.get(), octets.length);
            echange.getResponseBody().write(octets);
            echange.close();
        });
        serveur.start();
        ProprietesMedias base = VerificationFichiersTest.proprietes(DataSize.ofMegabytes(5));
        ProprietesMedias proprietes = new ProprietesMedias(base.cloudinaryUrl(), base.dossierRacine(), base.delai(),
                base.images(), base.svg(), base.videos(), base.suppression(),
                new ProprietesMedias.Youtube("http://127.0.0.1:" + serveur.getAddress().getPort() + "/oembed", DELAI));
        verification = new VerificationYoutubeOembed(proprietes, JsonMapper.builder().build());
    }

    @AfterEach
    void arreter() {
        serveur.stop(0);
    }

    @Test
    void uneVideoPubliqueEtIntegrableEstAcceptee() {
        corps.set("{\"title\":\"Test du Vivobook 15\",\"author_name\":\"Boutique\",\"type\":\"video\"}");

        VerificationYoutube.VideoYoutube video = verification.verifier("dQw4w9WgXcQ");

        assertThat(video.titre()).isEqualTo("Test du Vivobook 15");
        assertThat(video.auteur()).isEqualTo("Boutique");
        assertThat(requeteRecue.get()).contains("format=json")
                .contains("url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DdQw4w9WgXcQ");
    }

    @ParameterizedTest
    @CsvSource({"401, YOUTUBE_NON_INTEGRABLE, privée", "403, YOUTUBE_NON_INTEGRABLE, désactivé l'intégration",
            "404, YOUTUBE_INTROUVABLE, supprimée", "400, YOUTUBE_INTROUVABLE, n'existe pas"})
    void lesVideosPriveesSupprimeesOuNonIntegrablesSontRefusees(int code, String erreur, String message) {
        statut.set(code);
        corps.set("Unauthorized");

        assertThatThrownBy(() -> verification.verifier("dQw4w9WgXcQ"))
                .isInstanceOf(RegleMetierException.class)
                .hasFieldOrPropertyWithValue("code", erreur)
                .hasMessageContaining(message);
    }

    @Test
    void unYoutubeTropLentDonneUneErreurDIndisponibiliteDansLeDelai() {
        attente.set(Duration.ofSeconds(3));
        long debut = System.nanoTime();

        assertThatThrownBy(() -> verification.verifier("dQw4w9WgXcQ"))
                .isInstanceOf(YoutubeIndisponibleException.class)
                .hasMessageContaining("n'a pas répondu");
        assertThat(Duration.ofNanos(System.nanoTime() - debut)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void uneErreurServeurOuUneReponseIllisibleDonnentUneIndisponibilite() {
        statut.set(500);
        assertThatThrownBy(() -> verification.verifier("dQw4w9WgXcQ")).isInstanceOf(YoutubeIndisponibleException.class);

        statut.set(200);
        corps.set("<html>pas du json</html>");
        assertThatThrownBy(() -> verification.verifier("dQw4w9WgXcQ")).isInstanceOf(YoutubeIndisponibleException.class);
    }

    @Test
    void unYoutubeInjoignableDonneUneIndisponibilite() {
        serveur.stop(0);

        assertThatThrownBy(() -> verification.verifier("dQw4w9WgXcQ")).isInstanceOf(YoutubeIndisponibleException.class);
    }
}
