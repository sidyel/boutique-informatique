package sn.boutique.administration.journal;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Filet de sécurité du journal (ADR 0009 §9) : toute requête d'écriture {@code /api/admin/**} qui réussit sans qu'un service
 * ait écrit sa ligne détaillée reçoit une ligne <em>générique</em> (auteur, route, statut). Une action ajoutée plus tard sans
 * appel au journal est donc quand même tracée ; le test {@code JournalAdministrationTest} détecte ces oublis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JournalInterceptor implements HandlerInterceptor {

    private static final Set<String> METHODES_ECRITURE = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final JournalAdministration journal;

    @Override
    public void afterCompletion(HttpServletRequest requete, HttpServletResponse reponse, Object handler, Exception ex) {
        int statut = reponse.getStatus();
        if (!METHODES_ECRITURE.contains(requete.getMethod()) || statut < 200 || statut >= 300) {
            return;
        }
        if (requete.getAttribute(JournalAdministration.ATTRIBUT_ECRIT) != null) {
            return;
        }
        try {
            journal.enregistrerGenerique(requete, statut);
        } catch (RuntimeException e) {
            // L'action a déjà réussi : on ne peut plus l'annuler. L'échec du journal doit alerter, pas casser la réponse.
            log.error("ALERTE_JOURNAL : ligne générique non écrite pour {} {} ({})", requete.getMethod(),
                    requete.getRequestURI(), e.getMessage(), e);
        }
    }
}
