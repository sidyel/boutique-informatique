package sn.boutique.noyau.config;

import java.time.Duration;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Cache Redis des lectures du catalogue. Durée de vie par défaut : 10 minutes.
 * Les caches nommés (catalogue, paramètres…) seront déclarés au fil des phases.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    RedisCacheManagerBuilderCustomizer personnalisationCacheRedis() {
        return constructeur -> constructeur.cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .prefixCacheNameWith("boutique:")
                .disableCachingNullValues());
    }
}
