package sn.boutique.authentification.securite;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import sn.boutique.noyau.erreur.ProblemesHttp;
import tools.jackson.databind.json.JsonMapper;

/**
 * Réponses 401 et 403 de Spring Security au même format ProblemDetail que le reste de l'API.
 * Le détail reste volontairement vague : aucun indice sur la raison du rejet d'un jeton.
 */
@Component
@RequiredArgsConstructor
public class ReponsesSecurite implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;
    private final ProblemesHttp problemes;

    @Override
    public void commence(HttpServletRequest requete, HttpServletResponse reponse, AuthenticationException ex)
            throws IOException {
        reponse.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        ecrire(reponse, problemes.creer(HttpStatus.UNAUTHORIZED, "NON_AUTHENTIFIE",
                "Connectez-vous pour accéder à cette ressource.", requete.getRequestURI()));
    }

    @Override
    public void handle(HttpServletRequest requete, HttpServletResponse reponse, AccessDeniedException ex)
            throws IOException {
        ecrire(reponse, problemes.creer(HttpStatus.FORBIDDEN, "ACCES_REFUSE",
                "Vous n'avez pas les droits nécessaires pour cette action.", requete.getRequestURI()));
    }

    public ProblemesHttp problemes() {
        return problemes;
    }

    public void ecrire(HttpServletResponse reponse, ProblemDetail probleme) throws IOException {
        reponse.setStatus(probleme.getStatus());
        reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(reponse.getOutputStream(), probleme);
    }
}
