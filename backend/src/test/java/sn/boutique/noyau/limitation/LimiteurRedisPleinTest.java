package sn.boutique.noyau.limitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import sn.boutique.support.HorlogeReglable;

/**
 * Instance principale configurée comme en production ({@code volatile-ttl}).
 * <ul>
 *   <li>Remplie de compteurs (qui expirent tous) : Redis supprime les plus proches de leur fin au lieu de
 *       refuser les écritures ; la limitation continue de fonctionner (relecture de sécurité, I2).</li>
 *   <li>Remplie de clés sans expiration (impossibles à supprimer) : Redis refuse les écritures (OOM) ; le
 *       limiteur bascule sur le secours local, journalise l'alerte, et ne renvoie jamais 500.</li>
 * </ul>
 */
@ExtendWith(OutputCaptureExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LimiteurRedisPleinTest {

    private static final Regle REGLE = new Regle(2, Duration.ofMinutes(1));

    private final GenericContainer<?> redisPlein = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withCommand("redis-server", "--maxmemory-policy", "volatile-ttl")
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

    @BeforeEach
    void vider() {
        redis.execute((RedisCallback<Void>) connexion -> {
            connexion.serverCommands().setConfig("maxmemory", "0");
            connexion.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void desCompteursInventesEnMasseNeRemplissentPasRedisJusquAuRefus(CapturedOutput sortie) {
        LimiteurDebit limiteur = limiteur();
        // 400 Ko de marge : bien moins que les 20 000 compteurs que l'attaquant va créer
        definirMaxmemory(memoireUtilisee() + 400_000);

        for (int i = 0; i < 20_000; i++) {
            limiteur.verifier("connexion-email", REGLE, "empreinte-email-inventee-" + i);
        }

        assertThat(sortie.getOut()).doesNotContain(LimiteurDebit.ALERTE_REDIS_PLEIN)
                .doesNotContain(LimiteurDebit.ALERTE_REDIS_INDISPONIBLE);
        Long cles = redis.execute((RedisCallback<Long>) connexion -> connexion.serverCommands().dbSize());
        assertThat(cles).as("des compteurs ont été supprimés au lieu de refuser les écritures").isLessThan(20_000L);
        // La limitation fonctionne toujours, dans Redis
        limiteur.verifier("connexion-ip", REGLE, "41.82.9.9");
        limiteur.verifier("connexion-ip", REGLE, "41.82.9.9");
        assertThatThrownBy(() -> limiteur.verifier("connexion-ip", REGLE, "41.82.9.9"))
                .isInstanceOf(TropDeRequetesException.class);
    }

    @Test
    void redisPleinBasculeSurLeSecoursLocalEtJournaliseLAlerte(CapturedOutput sortie) {
        LimiteurDebit limiteur = limiteur();
        // Avant : la limite s'applique normalement (2 requêtes, la 3e est refusée)
        limiteur.verifier("connexion-ip", REGLE, "41.82.0.1");
        limiteur.verifier("connexion-ip", REGLE, "41.82.0.1");
        assertThatThrownBy(() -> limiteur.verifier("connexion-ip", REGLE, "41.82.0.1"))
                .isInstanceOf(TropDeRequetesException.class);

        remplir();
        assertThatThrownBy(() -> redis.opsForValue().set("sonde", "x"))
                .as("Redis plein refuse les écritures").isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("OOM");

        // Nouveau compteur (INCR refusé) : la requête passe, comptée par le secours local
        assertThatNoException().isThrownBy(() -> limiteur.verifier("connexion-ip", REGLE, "41.82.0.2"));
        assertThatNoException().isThrownBy(() -> limiteur.verifier("connexion-ip", REGLE, "41.82.0.2"));
        assertThatThrownBy(() -> limiteur.verifier("connexion-ip", REGLE, "41.82.0.2"))
                .as("Redis plein : la limite s'applique toujours").isInstanceOf(TropDeRequetesException.class);
        assertThat(sortie.getOut()).contains("ERROR").contains(LimiteurDebit.ALERTE_REDIS_PLEIN).contains("OOM");
        // volatile-ttl : pour faire de la place, Redis a supprimé les compteurs (seules clés à expiration)
        // avant de refuser les écritures ; le secours local a pris le relais
        assertThat(redis.opsForValue().get(LimiteurDebit.PREFIXE + "connexion-ip:41.82.0.1")).isNull();
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

    private LimiteurDebit limiteur() {
        HorlogeReglable horloge = new HorlogeReglable();
        return new LimiteurDebit(redis, proprietes(), new LimiteurLocal(horloge), horloge);
    }

    private long memoireUtilisee() {
        Properties info = redis.execute((RedisCallback<Properties>) connexion -> connexion.serverCommands().info("memory"));
        return Long.parseLong(info.getProperty("used_memory"));
    }

    private void definirMaxmemory(long octets) {
        redis.execute((RedisCallback<Void>) connexion -> {
            connexion.serverCommands().setConfig("maxmemory", String.valueOf(octets));
            return null;
        });
    }

    private static ProprietesLimitation proprietes() {
        return new ProprietesLimitation(true, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE);
    }
}
