package sn.boutique.noyau.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import sn.boutique.catalogue.dto.AccueilReponse;
import sn.boutique.catalogue.dto.CategorieNoeudReponse;
import sn.boutique.catalogue.dto.CategorieReponse;
import sn.boutique.catalogue.dto.FicheProduitReponse;
import sn.boutique.catalogue.dto.FiltresReponse;
import sn.boutique.catalogue.dto.MarqueReponse;
import sn.boutique.catalogue.dto.MarqueResumeReponse;
import sn.boutique.catalogue.dto.PageReponse;
import sn.boutique.catalogue.dto.ProduitResumeReponse;
import sn.boutique.contenu.dto.ParametresPublicsReponse;
import sn.boutique.marketing.dto.BanniereProgrammee;
import sn.boutique.noyau.cache.CachesPublics;
import sn.boutique.noyau.cache.JournalErreursCache;
import sn.boutique.noyau.cache.ProprietesCache;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

/**
 * Cache Redis des lectures publiques, sur une <strong>instance Redis dédiée</strong> (voir {@link ProprietesCache}).
 * <p>
 * La connexion du cache n'est volontairement pas un bean {@code RedisConnectionFactory} : Spring Boot garde
 * ainsi sa connexion auto-configurée vers l'instance principale (limitation du débit, indicateur de santé).
 * Une panne du cache ne rend donc pas l'application « DOWN » et ne bloque aucune requête
 * ({@link JournalErreursCache}).
 * <p>
 * L'intercepteur du cache passe avant celui des transactions : une entrée trouvée en cache n'ouvre ni
 * transaction ni connexion à la base.
 */
@Configuration
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 10)
public class CacheConfig implements CachingConfigurer {

    @Bean(destroyMethod = "destroy")
    ConnexionCache connexionCache(ProprietesCache proprietes) {
        ProprietesCache.Redis redis = proprietes.redis();
        RedisStandaloneConfiguration serveur = new RedisStandaloneConfiguration(redis.hote(), redis.port());
        if (redis.motDePasse() != null && !redis.motDePasse().isBlank()) {
            serveur.setPassword(RedisPassword.of(redis.motDePasse()));
        }
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(redis.delai())
                .clientOptions(ClientOptions.builder()
                        // Instance coupée : échec immédiat au lieu de mettre les commandes en attente
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .socketOptions(SocketOptions.builder().connectTimeout(redis.delai()).build())
                        .build())
                .build();
        LettuceConnectionFactory fabrique = new LettuceConnectionFactory(serveur, client);
        fabrique.afterPropertiesSet();
        fabrique.start();
        return new ConnexionCache(fabrique);
    }

    @Bean
    CacheManager cacheManager(ConnexionCache connexion, ProprietesCache proprietes, JsonMapper jsonMapper) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(proprietes.duree())
                .prefixCacheNameWith(CachesPublics.PREFIXE)
                .disableCachingNullValues();
        Map<String, JavaType> typesParCache = typesParCache(jsonMapper.getTypeFactory());
        if (!typesParCache.keySet().equals(new java.util.HashSet<>(CachesPublics.TOUS))) {
            throw new IllegalStateException("Chaque cache public doit avoir son type : " + CachesPublics.TOUS);
        }

        // SCAN plutôt que KEYS pour vider un cache : KEYS bloque Redis pendant le parcours.
        // Écritures immédiates : par défaut (Lettuce), Spring Data Redis 4 écrit en tâche de fond ; une mise en
        // cache pourrait alors arriver après une invalidation et resservir l'ancienne version jusqu'à expiration.
        RedisCacheWriter ecrivain = RedisCacheWriter.create(connexion.fabrique(), configuration -> configuration
                .batchStrategy(BatchStrategies.scan(500))
                .immediateWrites());
        RedisCacheManager.RedisCacheManagerBuilder constructeur = RedisCacheManager
                .builder(ecrivain)
                .cacheDefaults(base)
                .disableCreateOnMissingCache();
        typesParCache.forEach((nom, type) -> constructeur.withCacheConfiguration(nom,
                base.serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(jsonMapper, type)))));
        return constructeur.build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new JournalErreursCache();
    }

    /**
     * Type de la valeur de chaque cache public. Leur forme est couverte par {@code FormatCachesTest} : tout
     * changement oblige à incrémenter {@link CachesPublics#VERSION_FORMAT}.
     */
    public static Map<String, JavaType> typesParCache(TypeFactory types) {
        Map<String, JavaType> typesParCache = new HashMap<>();
        typesParCache.put(CachesPublics.ARBRE_CATEGORIES, liste(types, CategorieNoeudReponse.class));
        typesParCache.put(CachesPublics.CATEGORIE, types.constructType(CategorieReponse.class));
        typesParCache.put(CachesPublics.MARQUES, liste(types, MarqueResumeReponse.class));
        typesParCache.put(CachesPublics.MARQUE, types.constructType(MarqueReponse.class));
        typesParCache.put(CachesPublics.PARAMETRES, types.constructType(ParametresPublicsReponse.class));
        typesParCache.put(CachesPublics.BANNIERES, liste(types, BanniereProgrammee.class));
        typesParCache.put(CachesPublics.FICHE_PRODUIT, types.constructType(FicheProduitReponse.class));
        typesParCache.put(CachesPublics.PRODUITS_SIMILAIRES, liste(types, ProduitResumeReponse.class));
        typesParCache.put(CachesPublics.LISTE_PRODUITS,
                types.constructParametricType(PageReponse.class, ProduitResumeReponse.class));
        typesParCache.put(CachesPublics.FILTRES, types.constructType(FiltresReponse.class));
        typesParCache.put(CachesPublics.ACCUEIL, types.constructType(AccueilReponse.class));
        typesParCache.put(CachesPublics.SITEMAP, types.constructType(String.class));
        return typesParCache;
    }

    private static JavaType liste(TypeFactory types, Class<?> element) {
        return types.constructCollectionType(List.class, element);
    }

    /** Connexion à l'instance Redis du cache (enveloppée pour ne pas être un {@code RedisConnectionFactory}). */
    public record ConnexionCache(LettuceConnectionFactory fabrique) {
        public void destroy() {
            fabrique.destroy();
        }
    }
}
