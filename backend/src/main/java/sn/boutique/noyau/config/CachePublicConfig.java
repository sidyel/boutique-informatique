package sn.boutique.noyau.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
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
 * <p>Le compte, l'authentification et l'administration gardent le {@code no-store} de Spring Security.
 */
@Configuration
public class CachePublicConfig implements WebMvcConfigurer {

    /** Lectures publiques, identiques pour tous les visiteurs. */
    static final String[] LECTURES_PUBLIQUES = {
        "/api/accueil", "/api/bannieres", "/api/parametres", "/api/redirections",
        "/api/produits/**", "/api/recherche/**", "/api/categories/**", "/api/marques/**",
        "/api/produits", "/api/categories", "/api/marques",
    };

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
