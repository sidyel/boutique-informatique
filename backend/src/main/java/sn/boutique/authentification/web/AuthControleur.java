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
import sn.boutique.authentification.dto.EmailRequete;
import sn.boutique.authentification.dto.InscriptionRequete;
import sn.boutique.authentification.dto.JetonRequete;
import sn.boutique.authentification.dto.MessageReponse;
import sn.boutique.authentification.dto.ReinitialisationRequete;
import sn.boutique.authentification.securite.LimitesAuthentification;
import sn.boutique.authentification.service.AuthService;
import sn.boutique.authentification.service.IdentifiantsInvalidesException;
import sn.boutique.authentification.service.JetonInvalideException;
import sn.boutique.authentification.service.SecuriteCompteService;
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
    static final String MESSAGE_RENVOI =
            "Si un compte non vérifié correspond à cette adresse, un nouveau lien vient de lui être envoyé.";
    static final String MESSAGE_MOT_DE_PASSE_OUBLIE =
            "Si un compte correspond à cette adresse, un lien pour choisir un nouveau mot de passe vient de lui être envoyé.";

    private final AuthService authService;
    private final SessionService sessionService;
    private final SecuriteCompteService securiteCompte;
    private final CookieRafraichissement cookie;
    private final ProblemesHttp problemes;
    private final LimitesAuthentification limites;
    private final sn.boutique.compte.service.ChangementEmailService changementEmail;

    @Operation(summary = "Créer un compte client",
            description = "Réponse identique si l'e-mail a déjà un compte (son titulaire est prévenu par e-mail).")
    @PostMapping("/inscription")
    public ResponseEntity<MessageReponse> inscrire(@Valid @RequestBody InscriptionRequete requete,
                                                   HttpServletRequest http) {
        limites.inscription(http.getRemoteAddr());
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
        limites.connexion(http.getRemoteAddr(), requete.email());
        return reponseSession(authService.connecter(requete, contexte(http)));
    }

    @Operation(summary = "Obtenir un nouveau jeton d'accès (rotation du jeton de rafraîchissement)")
    @PostMapping("/rafraichir")
    public ResponseEntity<ConnexionReponse> rafraichir(
            @CookieValue(name = "${boutique.authentification.cookie.nom}", required = false) String jeton,
            HttpServletRequest http) {
        limites.rafraichir(http.getRemoteAddr());
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

    @Operation(summary = "Confirmer l'adresse e-mail (jeton reçu par e-mail, valable 24 h, usage unique)")
    @PostMapping("/verification-email")
    public MessageReponse verifierEmail(@Valid @RequestBody JetonRequete requete) {
        securiteCompte.verifierEmail(requete.jeton());
        return new MessageReponse("Votre adresse e-mail est confirmée.");
    }

    @Operation(summary = "Renvoyer l'e-mail de vérification", description = "Réponse 202 identique dans tous les cas.")
    @PostMapping("/renvoyer-verification")
    public ResponseEntity<MessageReponse> renvoyerVerification(@Valid @RequestBody EmailRequete requete,
                                                               HttpServletRequest http) {
        // Quota de l'e-mail dépassé : même réponse 202, sans envoi (rien n'est révélé, boîte protégée)
        if (limites.renvoiVerification(http.getRemoteAddr(), requete.email())) {
            securiteCompte.demanderRenvoiVerification(requete.email());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageReponse(MESSAGE_RENVOI));
    }

    @Operation(summary = "Mot de passe oublié", description = "Réponse 202 identique, que l'e-mail ait un compte ou non.")
    @PostMapping("/mot-de-passe-oublie")
    public ResponseEntity<MessageReponse> motDePasseOublie(@Valid @RequestBody EmailRequete requete,
                                                           HttpServletRequest http) {
        // Quota de l'e-mail dépassé : même réponse 202, sans envoi (rien n'est révélé, boîte protégée)
        if (limites.motDePasseOublie(http.getRemoteAddr(), requete.email())) {
            securiteCompte.demanderReinitialisation(requete.email());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageReponse(MESSAGE_MOT_DE_PASSE_OUBLIE));
    }

    @Operation(summary = "Choisir un nouveau mot de passe (jeton reçu par e-mail, valable 1 h)",
            description = "Révoque toutes les sessions du compte.")
    @PostMapping("/reinitialiser")
    public MessageReponse reinitialiser(@Valid @RequestBody ReinitialisationRequete requete) {
        securiteCompte.reinitialiserMotDePasse(requete.jeton(), requete.motDePasse());
        return new MessageReponse("Votre mot de passe a été modifié. Vous pouvez vous connecter.");
    }

    @Operation(summary = "Débloquer son compte (lien reçu par e-mail au verrouillage, valable 1 h, usage unique)",
            description = "Lève les verrouillages de l'adresse depuis toutes les IP.")
    @PostMapping("/deverrouiller")
    public MessageReponse deverrouiller(@Valid @RequestBody JetonRequete requete) {
        securiteCompte.deverrouiller(requete.jeton());
        return new MessageReponse("Votre compte est débloqué. Vous pouvez vous connecter.");
    }

    @Operation(summary = "Confirmer un changement d'adresse e-mail (lien reçu sur la nouvelle adresse, 24 h, usage unique)",
            description = "L'adresse du compte change et devient vérifiée ; les commandes invité de cette adresse sont rattachées.")
    @PostMapping("/confirmer-email")
    public MessageReponse confirmerEmail(@Valid @RequestBody JetonRequete requete) {
        changementEmail.confirmer(requete.jeton());
        return new MessageReponse("Votre nouvelle adresse e-mail est confirmée.");
    }

    @Operation(summary = "Signaler un changement d'e-mail non sollicité (lien « ce n'est pas moi » reçu sur l'ancienne adresse)",
            description = "Annule la demande ou rétablit l'ancienne adresse (7 jours après la confirmation) ; révoque toutes les "
                    + "sessions et envoie un lien de réinitialisation du mot de passe.")
    @PostMapping("/signaler-changement-email")
    public MessageReponse signalerChangementEmail(@Valid @RequestBody JetonRequete requete) {
        changementEmail.signaler(requete.jeton());
        return new MessageReponse("Merci. Le changement est annulé et nous vous avons envoyé un lien pour choisir un nouveau "
                + "mot de passe. Vos appareils ont été déconnectés.");
    }

    @ExceptionHandler(JetonInvalideException.class)
    ResponseEntity<ProblemDetail> jetonInvalide(JetonInvalideException ex, HttpServletRequest http) {
        log.info("Lien refusé : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemes.creer(HttpStatus.BAD_REQUEST, "JETON_INVALIDE",
                        "Ce lien n'est plus valable. Demandez-en un nouveau.", http.getRequestURI()));
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

    /** IP réelle : X-Forwarded-For n'est lu que s'il vient de nginx (valve RemoteIp, ADR 0003 §13). */
    private static Contexte contexte(HttpServletRequest http) {
        return new Contexte(http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }
}
