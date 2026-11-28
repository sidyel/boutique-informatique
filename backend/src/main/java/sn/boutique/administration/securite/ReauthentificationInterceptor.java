package sn.boutique.administration.securite;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import sn.boutique.administration.journal.JournalAdministration;
import sn.boutique.administration.journal.JournalAdministration.Acteur;
import sn.boutique.authentification.service.JetonAccesService;

/** Applique {@link ReauthentificationRequise} avant l'exécution de la route. */
@Component
@RequiredArgsConstructor
public class ReauthentificationInterceptor implements HandlerInterceptor {

    private final ReauthentificationService service;
    private final JournalAdministration journal;

    @Override
    public boolean preHandle(HttpServletRequest requete, HttpServletResponse reponse, Object handler) {
        if (!(handler instanceof HandlerMethod methode) || !exigee(methode)) {
            return true;
        }
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification != null && authentification.getPrincipal() instanceof Jwt jwt
                && service.estValide(famille(jwt))) {
            return true;
        }
        if (authentification != null && authentification.getPrincipal() instanceof Jwt jwt) {
            journal.enregistrerPour(new Acteur(Long.valueOf(jwt.getSubject()),
                            String.valueOf(jwt.getClaimAsString(JetonAccesService.REVENDICATION_EMAIL)), "INTERNE"),
                    "REAUTHENTIFICATION_MANQUANTE", null, null, requete.getMethod() + " " + requete.getRequestURI());
        }
        throw new ReauthentificationRequiseException("réauthentification absente ou expirée pour "
                + requete.getMethod() + " " + requete.getRequestURI());
    }

    static boolean exigee(HandlerMethod methode) {
        return AnnotatedElementUtils.hasAnnotation(methode.getMethod(), ReauthentificationRequise.class)
                || AnnotatedElementUtils.hasAnnotation(methode.getBeanType(), ReauthentificationRequise.class);
    }

    /** Famille de la session (revendication « sid ») ; un jeton sans session ne peut pas ouvrir de fenêtre. */
    public static UUID famille(Jwt jwt) {
        String sid = jwt.getClaimAsString(JetonAccesService.REVENDICATION_SESSION);
        try {
            return sid == null ? null : UUID.fromString(sid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
