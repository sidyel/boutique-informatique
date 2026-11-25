package sn.boutique.noyau.limitation;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;

/**
 * Limiteur de débit à fenêtre fixe dans Redis : partagé par toutes les instances du backend.
 * <p>
 * Le script Lua est atomique : incrément, pose de l'expiration à la première requête de la fenêtre et
 * lecture du temps restant en un seul aller-retour, sans course entre requêtes simultanées.
 * <p>
 * Si Redis est indisponible ou plein (instance principale en {@code noeviction} : écritures refusées), la requête est <strong>laissée passer</strong> (erreur journalisée) :
 * une panne de Redis ne doit pas empêcher les clients de se connecter. Le verrouillage par compte
 * (en base) continue de protéger contre la force brute.
 */
@Slf4j
@Component
public class LimiteurDebit {

    static final String PREFIXE = "limite:";

    private static final RedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return {n, ttl}
            """, List.class);

    private final StringRedisTemplate redis;
    private final ProprietesLimitation proprietes;

    public LimiteurDebit(StringRedisTemplate redis, ProprietesLimitation proprietes) {
        this.redis = redis;
        this.proprietes = proprietes;
    }

    /**
     * Compte une requête et lève {@link TropDeRequetesException} au-delà de la limite.
     *
     * @param nom nom de la règle (préfixe de la clé Redis, journaux)
     * @param cle IP ou empreinte de l'e-mail : jamais une donnée personnelle en clair
     */
    public void verifier(String nom, Regle regle, String cle) {
        if (!proprietes.actif()) {
            return;
        }
        List<?> resultat;
        try {
            resultat = redis.execute(SCRIPT, List.of(PREFIXE + nom + ":" + cle),
                    String.valueOf(regle.fenetre().toMillis()));
        } catch (DataAccessException e) {
            // Cause la plus précise : « OOM command not allowed… » (instance pleine) plutôt que « Error in execution »
            log.error("Limitation du débit indisponible (Redis) : requête laissée passer. {}",
                    NestedExceptionUtils.getMostSpecificCause(e).getMessage());
            return;
        }
        long nombre = ((Number) resultat.get(0)).longValue();
        long millisRestantes = ((Number) resultat.get(1)).longValue();
        if (nombre > regle.limite()) {
            long secondes = Math.max(1, (millisRestantes + 999) / 1000);
            log.warn("Limite de débit « {} » atteinte ({} requêtes, limite {}), réessai dans {} s",
                    nom, nombre, regle.limite(), secondes);
            throw new TropDeRequetesException(nom, secondes);
        }
    }
}
