package sn.boutique.authentification.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.authentification.dto.ConnexionReponse;
import sn.boutique.authentification.dto.ConnexionRequete;
import sn.boutique.authentification.dto.InscriptionRequete;
import sn.boutique.authentification.dto.MessageReponse;
import sn.boutique.authentification.service.AuthService;
import sn.boutique.authentification.service.IdentifiantsInvalidesException;
import sn.boutique.authentification.service.SessionInvalideException;
import sn.boutique.authentification.service.SessionService;
import sn.boutique.authentification.service.SessionService.Contexte;
import sn.boutique.authentification.service.SessionService.Session;
import sn.boutique.compte.dto.ProfilReponse;
import sn.boutique.noyau.erreur.ProblemesHttp;

/**
 * Inscription, connexion, rafraîchissement et déconnexion (ADR 0003).
 * {@code /rafraichir} et {@code /deconnexion} utilisent le cookie : l'en-tête Origin y est exigé
 * (VerificationOrigineFiltre).
 */
@Slf4j
@Tag(name = "Authentification")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthControleur {

    static final String MESSAGE_INSCRIPTION =
            "Inscription enregistrée. Consultez votre boîte mail pour confirmer votre adresse.";

    private final AuthService authService;
    private final SessionService sessionService;
    private final CookieRafraichissement cookie;
    private final ProblemesHttp problemes;

    @Operation(summary = "Créer un compte client",
            description = "Réponse identique si l'e-mail a déjà un compte (son titulaire est prévenu par e-mail).")
    @PostMapping("/inscription")
    public ResponseEntity<MessageReponse> inscrire(@Valid @RequestBody InscriptionRequete requete) {
        try {
            authService.inscrire(requete);
        } catch (DataIntegrityViolationException e) {
            // Deux inscriptions simultanées avec le même e-mail : la seconde reçoit la même réponse
            log.info("Inscription concurrente avec un e-mail déjà enregistré");
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageReponse(MESSAGE_INSCRIPTION));
    }

    @Operation(summary = "Se connecter", description = "Renvoie le jeton d'accès et pose le cookie de rafraîchissement.")
    @PostMapping("/connexion")
    public ResponseEntity<ConnexionReponse> connecter(@Valid @RequestBody ConnexionRequete requete,
                                                      HttpServletRequest http) {
        return reponseSession(authService.connecter(requete, contexte(http)));
    }

    @Operation(summary = "Obtenir un nouveau jeton d'accès (rotation du jeton de rafraîchissement)")
    @PostMapping("/rafraichir")
    public ResponseEntity<ConnexionReponse> rafraichir(
            @CookieValue(name = "${boutique.authentification.cookie.nom}", required = false) String jeton,
            HttpServletRequest http) {
        return reponseSession(sessionService.rafraichir(jeton, contexte(http)));
    }

    @Operation(summary = "Se déconnecter (révoque la session et efface le cookie)")
    @PostMapping("/deconnexion")
    public ResponseEntity<Void> deconnecter(
            @CookieValue(name = "${boutique.authentification.cookie.nom}", required = false) String jeton) {
        sessionService.fermer(jeton);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.effacer().toString())
                .build();
    }

    @ExceptionHandler(IdentifiantsInvalidesException.class)
    ResponseEntity<ProblemDetail> identifiantsInvalides(IdentifiantsInvalidesException ex, HttpServletRequest http) {
        log.info("Connexion refusée : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemes.creer(HttpStatus.UNAUTHORIZED, "IDENTIFIANTS_INVALIDES",
                        "E-mail ou mot de passe incorrect.", http.getRequestURI()));
    }

    /** La session est terminée : le cookie est effacé et le frontend renvoie vers la connexion. */
    @ExceptionHandler(SessionInvalideException.class)
    ResponseEntity<ProblemDetail> sessionInvalide(SessionInvalideException ex, HttpServletRequest http) {
        log.info("Rafraîchissement refusé : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookie.effacer().toString())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemes.creer(HttpStatus.UNAUTHORIZED, "SESSION_INVALIDE",
                        "Votre session a expiré. Veuillez vous reconnecter.", http.getRequestURI()));
    }

    private ResponseEntity<ConnexionReponse> reponseSession(Session session) {
        ConnexionReponse corps = new ConnexionReponse(session.jetonAcces().valeur(), "Bearer",
                session.jetonAcces().expireDans(), ProfilReponse.de(session.utilisateur()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookie.creer(session.jetonRafraichissement(), session.dureeRestante()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(corps);
    }

    /** L'IP réelle est fournie par nginx (X-Forwarded-For, server.forward-headers-strategy=framework). */
    private static Contexte contexte(HttpServletRequest http) {
        return new Contexte(http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }
}
