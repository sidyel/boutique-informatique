package sn.boutique.administration.securite;

import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.administration.journal.JournalAdministration;
import sn.boutique.authentification.service.CodeSecondFacteurInvalideException;
import sn.boutique.authentification.service.SessionService.Contexte;
import sn.boutique.noyau.erreur.ProblemesHttp;

/** Ouvre la fenêtre de réauthentification de la session courante (ADR 0009 §8). */
@Slf4j
@Tag(name = "Administration — sécurité")
@RestController
@RequestMapping("/api/admin/reauthentification")
@RequiredArgsConstructor
public class AdminReauthentificationControleur {

    public record Requete(@NotBlank @Size(max = 20) String code) {
    }

    private final ReauthentificationService service;
    private final JournalAdministration journal;

    @Operation(summary = "Saisir un code TOTP pour autoriser les actions sensibles pendant 10 minutes")
    @PostMapping
    public ResponseEntity<Map<String, Instant>> reauthentifier(@AuthenticationPrincipal Jwt jwt,
                                                               @Valid @RequestBody Requete requete,
                                                               HttpServletRequest http) {
        var famille = ReauthentificationInterceptor.famille(jwt);
        if (famille == null) {
            throw new ReauthentificationRequiseException("jeton sans session : réauthentification impossible");
        }
        Long utilisateurId = Long.valueOf(jwt.getSubject());
        Instant jusqua = service.ouvrir(utilisateurId, famille, requete.code(),
                new Contexte(http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT)));
        journal.enregistrer("REAUTHENTIFICATION", "Utilisateur", utilisateurId, null, Map.of("valideJusqu", jusqua.toString()));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(Map.of("valideJusqu", jusqua));
    }

    /** Erreurs communes à toute l'API d'administration (la réauthentification manquante vient d'un intercepteur). */
    @RestControllerAdvice
    @RequiredArgsConstructor
    static class Erreurs {

        private final ProblemesHttp problemes;

        @ExceptionHandler(ReauthentificationRequiseException.class)
        ResponseEntity<ProblemDetail> reauthentificationRequise(ReauthentificationRequiseException ex,
                                                                HttpServletRequest http) {
            log.info("Réauthentification requise : {}", ex.getMessage());
            return erreur(HttpStatus.FORBIDDEN, "REAUTHENTIFICATION_REQUISE",
                    "Cette action exige de confirmer votre identité avec un code de votre application d'authentification.", http);
        }

        @ExceptionHandler(SessionAdministrationInvalideException.class)
        ResponseEntity<ProblemDetail> sessionInvalide(SessionAdministrationInvalideException ex, HttpServletRequest http) {
            log.info("Session d'administration refusée : {}", ex.getMessage());
            return erreur(HttpStatus.UNAUTHORIZED, "SESSION_INVALIDE",
                    "Votre session n'est plus valable. Veuillez vous reconnecter.", http);
        }

        /** Hors connexion (elle a son propre gestionnaire) : un code faux est une erreur de saisie, pas une session expirée. */
        @ExceptionHandler(CodeSecondFacteurInvalideException.class)
        ResponseEntity<ProblemDetail> codeInvalide(CodeSecondFacteurInvalideException ex, HttpServletRequest http) {
            return erreur(HttpStatus.UNPROCESSABLE_CONTENT, "CODE_INVALIDE", "Code incorrect ou déjà utilisé.", http);
        }

        private ResponseEntity<ProblemDetail> erreur(HttpStatus statut, String code, String message, HttpServletRequest http) {
            return ResponseEntity.status(statut).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(problemes.creer(statut, code, message, http.getRequestURI()));
        }
    }
}
