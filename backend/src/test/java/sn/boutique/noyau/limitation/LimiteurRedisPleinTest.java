package sn.boutique.noyau.limitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;

/**
 * Instance principale configurée comme en production ({@code noeviction}) puis remplie : Redis refuse alors
 * les écritures (erreur OOM). Le limiteur doit laisser passer la requête et journaliser, jamais renvoyer 500.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LimiteurRedisPleinTest {

    private static final Regle REGLE = new Regle(2, Duration.ofMinutes(1));

    private final GenericContainer<?> redisPlein = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withCommand("redis-server", "--maxmemory-policy", "noeviction")
            .withExposedPorts(6379);
    private LettuceConnectionFactory fabrique;
    private StringRedisTemplate redis;

    @BeforeAll
    void demarrer() {
        redisPlein.start();
        fabrique = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redisPlein.getHost(), redisPlein.getMappedPort(6379)));
        fabrique.afterPropertiesSet();
        fabrique.start();
        redis = new StringRedisTemplate(fabrique);
    }

    @AfterAll
    void arreter() {
        fabrique.destroy();
        redisPlein.stop();
    }

    @Test
    void redisPleinLaisseLaRequetePasserEtJournalise(CapturedOutput sortie) {
        LimiteurDebit limiteur = new LimiteurDebit(redis, proprietes());
        // Avant : la limite s'applique normalement (2 requêtes, la 3e est refusée)
        limiteur.verifier("connexion-ip", REGLE, "41.82.0.1");
        limiteur.verifier("connexion-ip", REGLE, "41.82.0.1");
        assertThatThrownBy(() -> limiteur.verifier("connexion-ip", REGLE, "41.82.0.1"))
                .isInstanceOf(TropDeRequetesException.class);

        remplir();
        assertThatThrownBy(() -> redis.opsForValue().set("sonde", "x"))
                .as("Redis plein refuse les écritures").isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("OOM");

        // Nouveau compteur (INCR refusé) : la requête passe
        assertThatNoException().isThrownBy(() -> limiteur.verifier("connexion-ip", REGLE, "41.82.0.2"));
        assertThat(sortie.getOut()).contains("Limitation du débit indisponible (Redis) : requête laissée passer.")
                .contains("OOM");
        // Les compteurs existants ne sont pas évincés : la fenêtre en cours n'est pas remise à zéro
        assertThat(redis.opsForValue().get(LimiteurDebit.PREFIXE + "connexion-ip:41.82.0.1")).isEqualTo("3");
    }

    /**
     * Écrit 3 Mo puis abaisse maxmemory à 1 Mo : l'instance est nettement au-dessus de sa limite, quelle que
     * soit la taille de la commande suivante (remplir « jusqu'au refus » laisse un état limite instable).
     */
    private void remplir() {
        String valeur = "x".repeat(100_000);
        for (int i = 0; i < 30; i++) {
            redis.opsForValue().set("remplissage:" + i, valeur);
        }
        redis.execute((RedisCallback<Void>) connexion -> {
            connexion.serverCommands().setConfig("maxmemory", "1mb");
            return null;
        });
    }

    private static ProprietesLimitation proprietes() {
        return new ProprietesLimitation(true, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE);
    }
}
