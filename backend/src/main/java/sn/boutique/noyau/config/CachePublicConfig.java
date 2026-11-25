package sn.boutique.noyau.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.WebContentInterceptor;

/**
 * En-tête {@code Cache-Control} des lectures publiques du catalogue : {@code max-age=0, must-revalidate, public}.
 *
 * <p>Spring Security envoie par défaut {@code no-cache, no-store} partout. Or le rendu serveur d'Angular
 * refuse de transmettre au navigateur une réponse {@code no-store}, {@code no-cache} ou {@code private}
 * (transfer cache) : le navigateur rappelait alors toute l'API déjà lue par le SSR (double chargement sur
 * un forfait data limité). Ces réponses sont identiques pour tous les visiteurs ; {@code max-age=0} et
 * {@code must-revalidate} imposent quand même de redemander au serveur à chaque lecture : aucune donnée
 * périmée (prix, stock). Spring Security n'écrase pas un {@code Cache-Control} déjà présent.
 *
 * <p>Revalidation économe : chaque réponse porte un {@code ETag} (empreinte du corps). Quand rien n'a
 * changé, la revalidation répond {@code 304} sans corps : le client mobile ne retélécharge pas le JSON.
 * Le serveur calcule toujours la réponse (servie depuis Redis) ; seul le transfert est économisé.
 *
 * <p>Le compte, l'authentification et l'administration gardent le {@code no-store} de Spring Security,
 * sans ETag : aucune réponse personnalisée n'est jamais marquée {@code public} (contrôlé par
 * {@code CachePublicTest}). Toute nouvelle route personnalisée (panier, commandes) reste hors de ces listes.
 */
@Configuration
public class CachePublicConfig implements WebMvcConfigurer {

    /** Lectures publiques, identiques pour tous les visiteurs. */
    private static final String[] LECTURES_PUBLIQUES = {
        "/api/accueil", "/api/bannieres", "/api/parametres", "/api/redirections",
        "/api/produits/**", "/api/recherche/**", "/api/categories/**", "/api/marques/**",
        "/api/produits", "/api/categories", "/api/marques",
    };

    /** Mêmes routes, au format des motifs de filtre servlet ({@code /prefixe/*} couvre aussi {@code /prefixe}). */
    private static final String[] MOTIFS_FILTRE_PUBLICS = {
        "/api/accueil", "/api/bannieres", "/api/parametres", "/api/redirections",
        "/api/produits/*", "/api/recherche/*", "/api/categories/*", "/api/marques/*",
    };

    /** Motifs de chemin (Spring) des lectures publiques : lus par le test garde-fou. */
    public static List<String> lecturesPubliques() {
        return List.of(LECTURES_PUBLIQUES);
    }

    /** Motifs du filtre ETag (servlet) : lus par le test garde-fou. */
    public static List<String> motifsFiltre() {
        return List.of(MOTIFS_FILTRE_PUBLICS);
    }

    /**
     * ETag calculé sur le corps des lectures publiques. nginx le rend faible ({@code W/"…"}) quand il
     * compresse la réponse ; la comparaison de {@code If-None-Match} d'une requête GET est faible : le 304
     * fonctionne aussi derrière la compression.
     */
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> filtreEtagLecturesPubliques() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> enregistrement =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        enregistrement.addUrlPatterns(MOTIFS_FILTRE_PUBLICS);
        enregistrement.setName("filtreEtagLecturesPubliques");
        return enregistrement;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registre) {
        WebContentInterceptor intercepteur = new WebContentInterceptor();
        CacheControl revalidationPublique = CacheControl.maxAge(Duration.ZERO).mustRevalidate().cachePublic();
        for (String chemin : LECTURES_PUBLIQUES) {
            intercepteur.addCacheMapping(revalidationPublique, chemin);
        }
        registre.addInterceptor(intercepteur).addPathPatterns(LECTURES_PUBLIQUES);
    }
}
