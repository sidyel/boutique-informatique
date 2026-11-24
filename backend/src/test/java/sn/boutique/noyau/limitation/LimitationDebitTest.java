package sn.boutique.noyau.limitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import sn.boutique.TestcontainersConfiguration;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;

/**
 * Limitation du débit, testée par de vraies requêtes HTTP sur le serveur Tomcat (la valve RemoteIp
 * n'existe pas dans MockMvc). Les requêtes viennent de 127.0.0.1, qui n'est PAS un proxy interne :
 * leur X-Forwarded-For doit être ignoré.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LimitationDebitTest {

    private static final String ORIGINE = "http://localhost:4200";

    @LocalServerPort
    private int port;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private LimiteurDebit limiteur;
    @Value("${server.tomcat.remoteip.internal-proxies}")
    private String proxiesInternes;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void viderLesCompteurs() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void unFauxXForwardedForDifferentAChaqueEssaiNeContournePasLaLimite() throws Exception {
        for (int i = 1; i <= 30; i++) {
            HttpResponse<String> reponse = connexion("inconnu" + i + "@exemple.sn", "196.207.%d.%d".formatted(i, i));
            assertThat(reponse.statusCode()).as("tentative %d", i).isEqualTo(401);
        }

        HttpResponse<String> bloquee = connexion("encore-un@exemple.sn", "41.82.200.31");

        assertThat(bloquee.statusCode()).isEqualTo(429);
        assertThat(bloquee.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(bloquee.body())
                .contains("\"code\":\"TROP_DE_REQUETES\"", "Trop de tentatives. Réessayez dans une minute.");
        long reessayerDans = Long.parseLong(bloquee.headers().firstValue("Retry-After").orElseThrow());
        assertThat(reessayerDans).isBetween(1L, 60L);
        assertThat(bloquee.body()).contains("\"reessayerDans\":" + reessayerDans);
    }

    @Test
    void connexionDixParHeureEtParEmailQuelleQueSoitLaCasse() throws Exception {
        String email = "cible-" + UUID.randomUUID() + "@exemple.sn";
        for (int i = 1; i <= 10; i++) {
            String saisie = i % 2 == 0 ? email.toUpperCase() : " " + email;
            assertThat(connexion(saisie, null).statusCode()).as("tentative %d", i).isEqualTo(401);
        }
        HttpResponse<String> bloquee = connexion(email, null);
        assertThat(bloquee.statusCode()).isEqualTo(429);
        assertThat(Long.parseLong(bloquee.headers().firstValue("Retry-After").orElseThrow())).isBetween(3000L, 3600L);

        // Les autres comptes restent accessibles depuis la même IP
        assertThat(connexion("autre-" + UUID.randomUUID() + "@exemple.sn", null).statusCode()).isEqualTo(401);
    }

    @Test
    void inscriptionTrenteParHeureEtParIp() throws Exception {
        for (int i = 1; i <= 30; i++) {
            assertThat(inscription("client" + i + "-" + UUID.randomUUID() + "@exemple.sn").statusCode())
                    .as("inscription %d", i).isEqualTo(202);
        }
        assertThat(inscription("client31-" + UUID.randomUUID() + "@exemple.sn").statusCode()).isEqualTo(429);
    }

    @Test
    void motDePasseOublieEtRenvoiTroisParHeureEtParEmail() throws Exception {
        for (String route : new String[] {"/api/auth/mot-de-passe-oublie", "/api/auth/renvoyer-verification"}) {
            String email = "oubli-" + UUID.randomUUID() + "@exemple.sn";
            for (int i = 1; i <= 3; i++) {
                assertThat(post(route, "{\"email\":\"" + email + "\"}", null, null).statusCode()).isEqualTo(202);
            }
            assertThat(post(route, "{\"email\":\"" + email + "\"}", null, null).statusCode()).isEqualTo(429);
            // Même réponse 429 pour un e-mail qui n'a pas de compte : la limite ne révèle rien
            assertThat(post(route, "{\"email\":\"" + UUID.randomUUID() + "@exemple.sn\"}", null, null).statusCode())
                    .isEqualTo(202);
        }
    }

    @Test
    void motDePasseOublieDixParHeureEtParIp() throws Exception {
        for (int i = 1; i <= 10; i++) {
            String corps = "{\"email\":\"ip-" + UUID.randomUUID() + "@exemple.sn\"}";
            assertThat(post("/api/auth/mot-de-passe-oublie", corps, null, "10.0.0." + i).statusCode()).isEqualTo(202);
        }
        String corps = "{\"email\":\"ip-" + UUID.randomUUID() + "@exemple.sn\"}";
        assertThat(post("/api/auth/mot-de-passe-oublie", corps, null, "10.0.0.99").statusCode()).isEqualTo(429);
    }

    @Test
    void rafraichirTrenteParMinuteEtParIp() throws Exception {
        for (int i = 1; i <= 30; i++) {
            assertThat(post("/api/auth/rafraichir", "", ORIGINE, "172.20.0." + i).statusCode()).isEqualTo(401);
        }
        assertThat(post("/api/auth/rafraichir", "", ORIGINE, null).statusCode()).isEqualTo(429);
    }

    @Test
    void laFenetreSeRouvreASonExpiration() throws Exception {
        Regle regle = new Regle(2, Duration.ofMillis(800));
        String cle = UUID.randomUUID().toString();
        limiteur.verifier("essai", regle, cle);
        limiteur.verifier("essai", regle, cle);
        assertThatThrownBy(() -> limiteur.verifier("essai", regle, cle))
                .isInstanceOf(TropDeRequetesException.class)
                .satisfies(e -> assertThat(((TropDeRequetesException) e).getReessayerDans()).isEqualTo(1));

        Thread.sleep(900);
        assertThatNoException().isThrownBy(() -> limiteur.verifier("essai", regle, cle));
    }

    @Test
    void redisNeStockeAucuneAdresseEmailEnClair() throws Exception {
        String email = "secret-" + UUID.randomUUID() + "@exemple.sn";
        connexion(email, null);

        assertThat(redis.keys("limite:*")).isNotEmpty().noneMatch(cle -> cle.contains("@") || cle.contains("secret-"));
    }

    @Test
    void seulsLesReseauxDockerSontDesProxiesDeConfiance() {
        Pattern proxies = Pattern.compile(proxiesInternes);

        assertThat(proxies.matcher("172.18.0.5").matches()).isTrue();
        assertThat(proxies.matcher("10.0.3.7").matches()).isTrue();
        assertThat(proxies.matcher("192.168.32.2").matches()).isTrue();
        assertThat(proxies.matcher("127.0.0.1").matches()).isFalse();
        assertThat(proxies.matcher("172.32.0.1").matches()).isFalse();
        assertThat(proxies.matcher("41.82.10.10").matches()).isFalse();
        assertThat(proxies.matcher("196.207.1.1").matches()).isFalse();
    }

    private HttpResponse<String> connexion(String email, String fauxXForwardedFor) throws Exception {
        return post("/api/auth/connexion", "{\"email\":\"" + email + "\",\"motDePasse\":\"Mauvais-mot-de-passe\"}",
                null, fauxXForwardedFor);
    }

    private HttpResponse<String> inscription(String email) throws Exception {
        return post("/api/auth/inscription", "{\"email\":\"" + email
                + "\",\"motDePasse\":\"Thieboudienne-2026\",\"prenom\":\"Awa\",\"nom\":\"Diop\"}", null, null);
    }

    private HttpResponse<String> post(String route, String corps, String origine, String fauxXForwardedFor)
            throws Exception {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corps));
        if (origine != null) {
            requete.header("Origin", origine);
        }
        if (fauxXForwardedFor != null) {
            requete.header("X-Forwarded-For", fauxXForwardedFor);
            requete.header("X-Real-IP", fauxXForwardedFor);
        }
        return http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
    }
}
