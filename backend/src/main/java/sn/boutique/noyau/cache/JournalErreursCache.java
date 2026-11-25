package sn.boutique.noyau.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Le cache est une optimisation : s'il est indisponible (instance arrêtée, délai dépassé, entrée illisible
 * après un déploiement), l'erreur est journalisée et la requête lit la base comme sans cache.
 * <p>
 * Seul risque : une invalidation perdue laisse une entrée périmée jusqu'à sa durée de vie (10 minutes).
 */
@Slf4j
public class JournalErreursCache implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException e, Cache cache, Object cle) {
        log.warn("Cache « {} » illisible (clé {}) : lecture en base. {}", cache.getName(), cle, e.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException e, Cache cache, Object cle, Object valeur) {
        log.warn("Cache « {} » : écriture impossible (clé {}). {}", cache.getName(), cle, e.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException e, Cache cache, Object cle) {
        log.error("Cache « {} » : invalidation impossible (clé {}), entrée périmée jusqu'à expiration. {}",
                cache.getName(), cle, e.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException e, Cache cache) {
        log.error("Cache « {} » : vidage impossible, entrées périmées jusqu'à expiration. {}",
                cache.getName(), e.getMessage());
    }
}
