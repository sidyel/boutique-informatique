package sn.boutique.noyau.limitation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
 * <strong>Redis en erreur</strong> (arrêté, injoignable, ou plein : écritures refusées avec « OOM ») : la
 * requête est comptée par le {@link LimiteurLocal limiteur de secours} en mémoire de l'instance, et la panne
 * journalisée au niveau ERROR avec un marqueur stable, à brancher sur une alerte :
 * {@value #ALERTE_REDIS_PLEIN} ou {@value #ALERTE_REDIS_INDISPONIBLE}. Le message est répété au plus toutes
 * les {@code 30 s} par marqueur, avec le nombre d'erreurs depuis le précédent (pas d'avalanche de journaux).
 * <p>
 * L'instance principale est en {@code volatile-ttl} : toutes ses clés ont une expiration, Redis supprime
 * donc les compteurs les plus proches de leur fin au lieu de refuser les écritures (relecture de sécurité, I2).
 */
@Slf4j
@Component
public class LimiteurDebit {

    static final String PREFIXE = "limite:";
    /** Marqueur des journaux : Redis plein (écritures refusées). Recherché par l'alerte de supervision. */
    public static final String ALERTE_REDIS_PLEIN = "ALERTE_REDIS_PLEIN";
    /** Marqueur des journaux : Redis arrêté ou injoignable. */
    public static final String ALERTE_REDIS_INDISPONIBLE = "ALERTE_REDIS_INDISPONIBLE";
    static final Duration INTERVALLE_JOURNAL = Duration.ofSeconds(30);

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
    private final LimiteurLocal secours;
    private final Clock horloge;
    private final Map<String, Instant> derniersJournaux = new ConcurrentHashMap<>();
    private final AtomicLong erreursNonJournalisees = new AtomicLong();

    public LimiteurDebit(StringRedisTemplate redis, ProprietesLimitation proprietes, LimiteurLocal secours,
                         Clock horloge) {
        this.redis = redis;
        this.proprietes = proprietes;
        this.secours = secours;
        this.horloge = horloge;
    }

    /**
     * Compte une requête et lève {@link TropDeRequetesException} au-delà de la limite.
     *
     * @param nom nom de la règle (préfixe de la clé Redis, journaux)
     * @param cle IP ou empreinte de l'e-mail : jamais une donnée personnelle en clair
     */
    public void verifier(String nom, Regle regle, String cle) {
        Comptage comptage = compter(nom, regle, cle);
        if (comptage.depasse(regle)) {
            long secondes = comptage.reessayerDans();
            log.warn("Limite de débit « {} » atteinte ({} requêtes, limite {}), réessai dans {} s",
                    nom, comptage.nombre(), regle.limite(), secondes);
            throw new TropDeRequetesException(nom, secondes);
        }
    }

    /**
     * Compte une requête <strong>sans rien bloquer</strong> : à l'appelant de décider (alerte, envoi d'e-mail
     * omis…). Limitation désactivée : comptage nul.
     */
    public Comptage compter(String nom, Regle regle, String cle) {
        if (!proprietes.actif()) {
            return new Comptage(0, 0);
        }
        List<?> resultat;
        try {
            resultat = redis.execute(SCRIPT, List.of(PREFIXE + nom + ":" + cle),
                    String.valueOf(regle.fenetre().toMillis()));
        } catch (DataAccessException e) {
            signalerPanne(e);
            return secours.compter(nom, regle, cle);
        }
        return new Comptage(((Number) resultat.get(0)).longValue(), ((Number) resultat.get(1)).longValue());
    }

    private void signalerPanne(DataAccessException e) {
        // Cause la plus précise : « OOM command not allowed… » (instance pleine) plutôt que « Error in execution »
        String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        String marqueur = cause.contains("OOM") ? ALERTE_REDIS_PLEIN : ALERTE_REDIS_INDISPONIBLE;
        long erreurs = erreursNonJournalisees.incrementAndGet();
        Instant maintenant = horloge.instant();
        Instant dernier = derniersJournaux.get(marqueur);
        if (dernier != null && maintenant.isBefore(dernier.plus(INTERVALLE_JOURNAL))) {
            return;
        }
        derniersJournaux.put(marqueur, maintenant);
        erreursNonJournalisees.addAndGet(-erreurs);
        log.error("{} : limitation du débit assurée par le limiteur de secours local ({} erreur(s) Redis depuis "
                + "le message précédent). Cause : {}", marqueur, erreurs, cause);
    }
}
