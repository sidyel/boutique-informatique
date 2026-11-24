package sn.boutique.noyau.limitation;

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

class LimiteurDebitTest {

    private static final Regle REGLE = new Regle(1, Duration.ofMinutes(1));

    @Test
    @SuppressWarnings("unchecked")
    void redisIndisponibleLaisseLaRequetePasser() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("Redis arrêté"));
        LimiteurDebit limiteur = new LimiteurDebit(redis, proprietes(true));

        assertThatNoException().isThrownBy(() -> limiteur.verifier("connexion-ip", REGLE, "41.82.1.1"));
    }

    @Test
    void limitationDesactiveeNInterrogePasRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        new LimiteurDebit(redis, proprietes(false)).verifier("connexion-ip", REGLE, "41.82.1.1");

        verifyNoInteractions(redis);
    }

    private static ProprietesLimitation proprietes(boolean actif) {
        return new ProprietesLimitation(actif, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE);
    }
}
