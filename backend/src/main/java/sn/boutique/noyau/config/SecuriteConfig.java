package sn.boutique.noyau.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import sn.boutique.authentification.securite.ReponsesSecurite;
import sn.boutique.authentification.securite.VerificationOrigineFiltre;
import sn.boutique.authentification.service.JetonAccesService;
import sn.boutique.compte.modele.Role;

/**
 * Sécurité de l'API (ADR 0003) : sans état, authentification par jeton d'accès JWT
 * ({@code Authorization: Bearer}). Seules {@code /api/auth/rafraichir} et {@code /api/auth/deconnexion}
 * reposent sur un cookie, protégées contre le CSRF par SameSite=Strict et la vérification de l'Origin.
 * Le CSRF de Spring est donc désactivé : aucune autre route n'utilise de cookie.
 */
@Configuration
@EnableWebSecurity
public class SecuriteConfig {

    /** Routes qui reposent sur un cookie : Origin obligatoire sur toute requête qui modifie un état (CSRF). */
    public static final List<String> ROUTES_AVEC_COOKIE = List.of("/api/auth/rafraichir", "/api/auth/deconnexion",
            "/api/auth/second-facteur/**", "/api/panier", "/api/panier/**", "/api/commandes", "/api/commandes/**");

    private static final String[] ROUTES_DOCUMENTATION = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    private static final String[] ROLES_ADMINISTRATION = {Role.ADMIN.name(), Role.SUPER_ADMIN.name()};
    /** Photos et vidéos des produits : aussi la gestion du stock (elle reçoit la marchandise et la photographie). */
    private static final String[] ROLES_MEDIAS = {
            Role.GESTIONNAIRE_STOCK.name(), Role.ADMIN.name(), Role.SUPER_ADMIN.name()
    };
    private static final String[] ROLES_INTERNES = {
            Role.SERVICE_CLIENT.name(), Role.GESTIONNAIRE_STOCK.name(), Role.ADMIN.name(), Role.SUPER_ADMIN.name()
    };

    @Bean
    SecurityFilterChain chaineFiltresSecurite(HttpSecurity http, ReponsesSecurite reponses,
                                              ProprietesBoutique proprietes) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(formulaire -> formulaire.disable())
                .logout(deconnexion -> deconnexion.disable())
                .requestCache(cache -> cache.disable())
                .oauth2ResourceServer(serveur -> serveur
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertisseurJwt()))
                        .authenticationEntryPoint(reponses)
                        .accessDeniedHandler(reponses))
                .exceptionHandling(erreurs -> erreurs
                        .authenticationEntryPoint(reponses)
                        .accessDeniedHandler(reponses))
                .addFilterBefore(new VerificationOrigineFiltre(originesDuSite(proprietes), ROUTES_AVEC_COOKIE, reponses),
                        BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(autorisations -> autorisations
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Prix et publication des variantes, identité du site, bannières :
                        // ni le service client ni la gestion du stock
                        .requestMatchers("/api/admin/variantes/**", "/api/admin/parametres/**",
                                "/api/admin/bannieres/**").hasAnyRole(ROLES_ADMINISTRATION)
                        // Comptes de l'équipe et journal des actions : super-administrateur seulement (ADR 0009 §6)
                        .requestMatchers("/api/admin/utilisateurs/**", "/api/admin/utilisateurs", "/api/admin/journal/**",
                                "/api/admin/journal").hasRole(Role.SUPER_ADMIN.name())
                        // Un avoir est une pièce comptable : réservé à l'administration
                        .requestMatchers(HttpMethod.POST, "/api/admin/factures/*/avoirs")
                        .hasAnyRole(ROLES_ADMINISTRATION)
                        .requestMatchers("/api/admin/produits/*/medias/**", "/api/admin/medias/**")
                        .hasAnyRole(ROLES_MEDIAS)
                        .requestMatchers("/api/admin/**").hasAnyRole(ROLES_INTERNES)
                        .requestMatchers("/api/compte/**").authenticated()
                        .requestMatchers("/api/**", "/error").permitAll()
                        // Référencement (ADR 0007) : lus par les moteurs de recherche
                        .requestMatchers("/robots.txt", "/sitemap.xml", "/sitemap-*.xml").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(ROUTES_DOCUMENTATION).permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    /** Revendication « roles » du JWT → autorités ROLE_CLIENT, ROLE_ADMIN… ; principal = sub (identifiant). */
    static JwtAuthenticationConverter convertisseurJwt() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(JetonAccesService.REVENDICATION_ROLES);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertisseur = new JwtAuthenticationConverter();
        convertisseur.setJwtGrantedAuthoritiesConverter(roles);
        return convertisseur;
    }

    /** Origines acceptées par la vérification CSRF : l'URL du site et les origines CORS. */
    static List<String> originesDuSite(ProprietesBoutique proprietes) {
        List<String> origines = new ArrayList<>(proprietes.corsOrigines());
        origines.add(proprietes.urlSite());
        return origines;
    }

    @Bean
    CorsConfigurationSource sourceConfigurationCors(ProprietesBoutique proprietes) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(proprietes.corsOrigines());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
