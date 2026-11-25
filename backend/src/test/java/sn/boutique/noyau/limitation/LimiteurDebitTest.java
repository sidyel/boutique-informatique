package sn.boutique.noyau.limitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import sn.boutique.support.HorlogeReglable;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;

@ExtendWith(OutputCaptureExtension.class)
class LimiteurDebitTest {

    private static final Regle REGLE = new Regle(1, Duration.ofMinutes(1));

    private final HorlogeReglable horloge = new HorlogeReglable();

    @Test
    @SuppressWarnings("unchecked")
    void redisArreteBasculeSurLeLimiteurDeSecoursEtJournaliseUneAlerte(CapturedOutput sortie) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("Redis arrêté"));
        LimiteurDebit limiteur = new LimiteurDebit(redis, proprietes(true), new LimiteurLocal(horloge), horloge);

        assertThatNoException().isThrownBy(() -> limiteur.verifier("connexion-ip", REGLE, "41.82.1.1"));
        assertThatThrownBy(() -> limiteur.verifier("connexion-ip", REGLE, "41.82.1.1"))
                .as("la limite s'applique toujours pendant la panne").isInstanceOf(TropDeRequetesException.class);
        assertThat(sortie.getOut()).contains("ERROR").contains(LimiteurDebit.ALERTE_REDIS_INDISPONIBLE)
                .contains("Redis arrêté");
    }

    @Test
    @SuppressWarnings("unchecked")
    void lAlerteEstRepeteeAuPlusToutesLes30SecondesAvecLeNombreDErreurs(CapturedOutput sortie) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("Redis arrêté"));
        LimiteurDebit limiteur = new LimiteurDebit(redis, proprietes(true), new LimiteurLocal(horloge), horloge);

        for (int i = 0; i < 50; i++) {
            limiteur.verifier("connexion-ip", REGLE, "41.82.2." + i);
        }
        assertThat(occurrences(sortie.getOut(), LimiteurDebit.ALERTE_REDIS_INDISPONIBLE)).isEqualTo(1);

        horloge.avancer(LimiteurDebit.INTERVALLE_JOURNAL);
        limiteur.verifier("connexion-ip", REGLE, "41.82.3.1");
        assertThat(occurrences(sortie.getOut(), LimiteurDebit.ALERTE_REDIS_INDISPONIBLE)).isEqualTo(2);
        assertThat(sortie.getOut()).contains("(50 erreur(s) Redis depuis le message précédent)");
    }

    @Test
    void limitationDesactiveeNInterrogePasRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        new LimiteurDebit(redis, proprietes(false), new LimiteurLocal(horloge), horloge)
                .verifier("connexion-ip", REGLE, "41.82.1.1");

        verifyNoInteractions(redis);
    }

    private static int occurrences(String texte, String motif) {
        return texte.split(java.util.regex.Pattern.quote(motif), -1).length - 1;
    }

    private static ProprietesLimitation proprietes(boolean actif) {
        return new ProprietesLimitation(actif, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE);
    }
}
