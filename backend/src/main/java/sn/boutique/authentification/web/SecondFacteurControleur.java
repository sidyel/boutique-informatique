package sn.boutique.authentification.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.authentification.dto.CodeEnrolementRequete;
import sn.boutique.authentification.dto.CodesSecoursReponse;
import sn.boutique.authentification.dto.ConnexionReponse;
import sn.boutique.authentification.dto.EnrolementReponse;
import sn.boutique.authentification.dto.JetonEtapeRequete;
import sn.boutique.authentification.dto.VerificationSecondFacteurRequete;
import sn.boutique.authentification.service.CodeSecondFacteurInvalideException;
import sn.boutique.authentification.service.EtapeInvalideException;
import sn.boutique.authentification.service.SecondFacteurService;
import sn.boutique.authentification.service.SecondFacteurService.Resultat;
import sn.boutique.authentification.service.SessionService.Contexte;
import sn.boutique.compte.dto.ProfilReponse;
import sn.boutique.noyau.erreur.ProblemesHttp;

/**
 * Deuxième temps de la connexion des comptes internes (ADR 0009). Chaque route exige le jeton d'étape reçu après le mot de
 * passe ; aucune n'accepte un jeton d'accès. La session (jeton d'accès + cookie) n'est émise qu'ici, après un code valide
 * et la confirmation des codes de secours.
 */
@Slf4j
@Tag(name = "Second facteur")
@RestController
@RequestMapping("/api/auth/second-facteur")
@RequiredArgsConstructor
public class SecondFacteurControleur {

    private final SecondFacteurService service;
    private final CookieRafraichissement cookie;
    private final ProblemesHttp problemes;

    @Operation(summary = "Générer le secret et le QR code d'enrôlement (étape ENROLEMENT_REQUIS)")
    @PostMapping("/enrolement/demarrer")
    public ResponseEntity<EnrolementReponse> demarrer(@Valid @RequestBody JetonEtapeRequete requete) {
        var enrolement = service.demarrerEnrolement(requete.jetonEtape());
        return sansCache(new EnrolementReponse(enrolement.secret(), enrolement.uri(), enrolement.codeQr()));
    }

    @Operation(summary = "Confirmer l'enrôlement avec un premier code : active le second facteur et présente les codes de secours")
    @PostMapping("/enrolement/confirmer")
    public ResponseEntity<CodesSecoursReponse> confirmer(@Valid @RequestBody CodeEnrolementRequete requete,
                                                         HttpServletRequest http) {
        return codes(service.confirmerEnrolement(requete.jetonEtape(), requete.code(), contexte(http)));
    }

    @Operation(summary = "Saisir un code TOTP ou un code de secours (étape CODE_REQUIS)",
            description = "Renvoie la session, ou les codes de secours à confirmer s'ils ne l'ont jamais été.")
    @PostMapping("/verifier")
    public ResponseEntity<?> verifier(@Valid @RequestBody VerificationSecondFacteurRequete requete, HttpServletRequest http) {
        Resultat resultat = service.verifier(requete.jetonEtape(), requete.code(), requete.codeSecours(), contexte(http));
        return resultat.session() != null ? session(resultat) : codes(resultat);
    }

    @Operation(summary = "Confirmer que les codes de secours sont enregistrés (« je les ai imprimés ») : ouvre la session")
    @PostMapping("/codes-secours/confirmer")
    public ResponseEntity<ConnexionReponse> confirmerCodes(@Valid @RequestBody JetonEtapeRequete requete,
                                                           HttpServletRequest http) {
        return session(service.confirmerCodesSecours(requete.jetonEtape(), contexte(http)));
    }

    @ExceptionHandler(EtapeInvalideException.class)
    ResponseEntity<ProblemDetail> etapeInvalide(EtapeInvalideException ex, HttpServletRequest http) {
        log.info("Étape de connexion refusée : {}", ex.getMessage());
        return erreur(HttpStatus.UNAUTHORIZED, "ETAPE_INVALIDE",
                "Votre connexion a expiré. Saisissez de nouveau votre e-mail et votre mot de passe.", http);
    }

    @ExceptionHandler(CodeSecondFacteurInvalideException.class)
    ResponseEntity<ProblemDetail> codeInvalide(CodeSecondFacteurInvalideException ex, HttpServletRequest http) {
        return erreur(HttpStatus.UNAUTHORIZED, "CODE_INVALIDE", "Code incorrect ou déjà utilisé.", http);
    }

    private ResponseEntity<ProblemDetail> erreur(HttpStatus statut, String code, String message, HttpServletRequest http) {
        return ResponseEntity.status(statut).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(problemes.creer(statut, code, message, http.getRequestURI()));
    }

    private ResponseEntity<CodesSecoursReponse> codes(Resultat resultat) {
        return sansCache(new CodesSecoursReponse(resultat.etape(), resultat.jetonEtape(), resultat.codesSecours()));
    }

    private ResponseEntity<ConnexionReponse> session(Resultat resultat) {
        var session = resultat.session();
        ConnexionReponse corps = new ConnexionReponse(session.jetonAcces().valeur(), "Bearer",
                session.jetonAcces().expireDans(), ProfilReponse.de(session.utilisateur()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.creer(session.jetonRafraichissement(), session.dureeRestante()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(corps);
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corps);
    }

    private static Contexte contexte(HttpServletRequest http) {
        return new Contexte(http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }
}
