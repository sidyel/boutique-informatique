package sn.boutique.administration.securite;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import sn.boutique.authentification.service.JetonAccesService;
import sn.boutique.compte.modele.Role;

/**
 * Le jeton d'accès vit 15 minutes : un compte désactivé ou dont le rôle a changé ne doit pas agir pendant ce délai
 * (ADR 0009, limites). Chaque appel d'administration recontrôle donc, en base, que le compte est actif et que son rôle
 * est celui du jeton ; sinon 401 {@code SESSION_INVALIDE} (le frontend tente un rafraîchissement, qui échoue : sessions révoquées).
 */
@Component
@RequiredArgsConstructor
public class CompteActifInterceptor implements HandlerInterceptor {

    private final JdbcClient jdbc;

    @Override
    public boolean preHandle(HttpServletRequest requete, HttpServletResponse reponse, Object handler) {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !(authentification.getPrincipal() instanceof Jwt jwt)) {
            return true;
        }
        // Le rôle vient de la revendication du jeton (les autorités peuvent porter d'autres entrées, ex. le facteur d'authentification)
        List<String> rolesJeton = jwt.getClaimAsStringList(JetonAccesService.REVENDICATION_ROLES);
        String roleJeton = rolesJeton == null || rolesJeton.isEmpty() ? "" : rolesJeton.get(0);
        boolean valide = jdbc.sql("select actif, role from utilisateur where id = :id")
                .param("id", Long.valueOf(jwt.getSubject()))
                .query((rs, n) -> rs.getBoolean(1) && roleJeton.equals(rs.getString(2)) && Role.valueOf(rs.getString(2)).estInterne())
                .optional().orElse(false);
        if (!valide) {
            throw new SessionAdministrationInvalideException("compte " + jwt.getSubject()
                    + " désactivé, supprimé ou de rôle différent du jeton");
        }
        return true;
    }
}
