package sn.boutique.noyau.cache;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import sn.boutique.catalogue.evenement.CatalogueModifie;

class InvalidationCachesTest {

    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache cache = mock(Cache.class);
    private final InvalidationCaches invalidation = new InvalidationCaches(cacheManager);

    @Test
    void auDemarrageTousLesCachesPublicsSontVides() {
        // Une entrée écrite par la version précédente (DTO sans un champ ajouté depuis) ne doit pas être relue
        when(cacheManager.getCache(anyString())).thenReturn(cache);

        invalidation.viderAuDemarrage();

        CachesPublics.TOUS.forEach(nom -> verify(cacheManager).getCache(nom));
        verify(cache, times(CachesPublics.TOUS.size())).invalidate();
    }

    @Test
    void unCacheIndisponibleNEmpechePasDeViderLesAutres() {
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        when(cache.invalidate()).thenThrow(new IllegalStateException("Redis indisponible")).thenReturn(true);

        invalidation.vider(new CatalogueModifie("essai"));

        verify(cache, times(CachesPublics.TOUS.size())).invalidate();
    }
}
