package sn.boutique.authentification.securite;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
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
import sn.boutique.noyau.journal.MasqueJetons;

/**
 * Protection CSRF des routes qui reposent sur un cookie : jeton de rafraîchissement ({@code /api/auth/rafraichir},
 * {@code /api/auth/deconnexion}, SameSite=Strict), panier invité et commande ({@code /api/panier/**},
 * {@code /api/commandes/**}, SameSite=Lax).
 * <p>
 * Sur ces routes, toute requête qui modifie un état (POST, PUT, PATCH, DELETE) doit porter un en-tête
 * {@code Origin} appartenant aux origines du site. Les navigateurs l'envoient sur ces méthodes (même de même
 * origine) et une page ne peut pas le falsifier. Une requête sans Origin est refusée. Aucun jeton CSRF n'est
 * donc nécessaire (voir ADR 0003). Les lectures (GET, HEAD) ne modifient rien et ne sont pas filtrées.
 * <p>
 * Un chemin protégé est exact ({@code /api/panier}) ou un préfixe terminé par {@code /**}
 * ({@code /api/panier/**} couvre {@code /api/panier/lignes/12}, mais pas {@code /api/paniers}).
 */
@Slf4j
public class VerificationOrigineFiltre extends OncePerRequestFilter {

    private final Set<String> originesAutorisees;
    private static final Set<String> METHODES_SURES = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> cheminsProteges;
    private final List<String> prefixesProteges;
    private final ReponsesSecurite reponses;

    public VerificationOrigineFiltre(Collection<String> originesAutorisees, Collection<String> cheminsProteges,
                                     ReponsesSecurite reponses) {
        this.originesAutorisees = originesAutorisees.stream()
                .map(VerificationOrigineFiltre::normaliser)
                .collect(Collectors.toUnmodifiableSet());
        this.cheminsProteges = cheminsProteges.stream().filter(chemin -> !chemin.endsWith("/**"))
                .collect(Collectors.toUnmodifiableSet());
        this.prefixesProteges = cheminsProteges.stream().filter(chemin -> chemin.endsWith("/**"))
                .map(chemin -> chemin.substring(0, chemin.length() - 2))
                .toList();
        this.reponses = reponses;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return METHODES_SURES.contains(requete.getMethod()) || !estProtege(requete.getRequestURI());
    }

    private boolean estProtege(String chemin) {
        return cheminsProteges.contains(chemin) || prefixesProteges.stream().anyMatch(chemin::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        String origine = requete.getHeader(HttpHeaders.ORIGIN);
        if (origine == null || !originesAutorisees.contains(normaliser(origine))) {
            log.warn("Origine refusée sur {} : {}", MasqueJetons.adresse(requete.getRequestURI()), origine);
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
