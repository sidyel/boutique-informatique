package sn.boutique.authentification.securite;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protection CSRF des routes qui utilisent le cookie du jeton de rafraîchissement
 * ({@code /api/auth/rafraichir} et {@code /api/auth/deconnexion}), en complément de SameSite=Strict.
 * <p>
 * L'en-tête {@code Origin} est <strong>obligatoire</strong> et doit appartenir aux origines du site.
 * Les navigateurs l'envoient sur tout POST (même de même origine) et une page ne peut pas le falsifier.
 * Une requête sans Origin est refusée. Aucun jeton CSRF n'est donc nécessaire (voir ADR 0003).
 */
@Slf4j
public class VerificationOrigineFiltre extends OncePerRequestFilter {

    private final Set<String> originesAutorisees;
    private final Set<String> cheminsProteges;
    private final ReponsesSecurite reponses;

    public VerificationOrigineFiltre(Collection<String> originesAutorisees, Collection<String> cheminsProteges,
                                     ReponsesSecurite reponses) {
        this.originesAutorisees = originesAutorisees.stream()
                .map(VerificationOrigineFiltre::normaliser)
                .collect(Collectors.toUnmodifiableSet());
        this.cheminsProteges = Set.copyOf(cheminsProteges);
        this.reponses = reponses;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return !"POST".equals(requete.getMethod()) || !cheminsProteges.contains(requete.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        String origine = requete.getHeader(HttpHeaders.ORIGIN);
        if (origine == null || !originesAutorisees.contains(normaliser(origine))) {
            log.warn("Origine refusée sur {} : {}", requete.getRequestURI(), origine);
            reponses.ecrire(reponse, reponses.problemes().creer(HttpStatus.FORBIDDEN, "ORIGINE_REFUSEE",
                    "Requête refusée : origine non autorisée.", requete.getRequestURI()));
            return;
        }
        chaine.doFilter(requete, reponse);
    }

    /** Origine comparée sans barre finale, en minuscules (schéma et hôte ne tiennent pas compte de la casse). */
    static String normaliser(String origine) {
        String resultat = origine.strip();
        while (resultat.endsWith("/")) {
            resultat = resultat.substring(0, resultat.length() - 1);
        }
        return resultat.toLowerCase(Locale.ROOT);
    }
}
