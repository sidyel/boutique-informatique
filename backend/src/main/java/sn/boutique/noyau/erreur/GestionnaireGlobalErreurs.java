package sn.boutique.noyau.erreur;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import sn.boutique.noyau.limitation.TropDeRequetesException;

/**
 * Traduit toutes les erreurs de l'API au format ProblemDetail (RFC 9457), avec des messages en français.
 * <p>
 * Propriétés ajoutées : {@code code} (identifiant stable pour le frontend), {@code horodatage}
 * et, pour la validation, {@code erreurs} (liste de {@link ErreurChamp}).
 */
@Slf4j
@RestControllerAdvice
public class GestionnaireGlobalErreurs extends ResponseEntityExceptionHandler {

    private final ProblemesHttp problemes;

    public GestionnaireGlobalErreurs(ProblemesHttp problemes) {
        this.problemes = problemes;
    }

    private static final Map<Integer, String> DETAILS_PAR_DEFAUT = Map.ofEntries(
            Map.entry(400, "La requête est mal formée."),
            Map.entry(404, "La ressource demandée n'existe pas."),
            Map.entry(405, "Cette méthode HTTP n'est pas autorisée sur cette ressource."),
            Map.entry(406, "Le format de réponse demandé n'est pas disponible."),
            Map.entry(413, "Le contenu envoyé dépasse la taille autorisée."),
            Map.entry(415, "Le type de contenu envoyé n'est pas pris en charge."),
            Map.entry(500, "Une erreur inattendue est survenue. Veuillez réessayer plus tard."),
            Map.entry(503, "Le service est momentanément indisponible."));

    // ---------------------------------------------------------------------
    // Exceptions métier de l'application
    // ---------------------------------------------------------------------

    @ExceptionHandler(RessourceIntrouvableException.class)
    ProblemDetail gererRessourceIntrouvable(RessourceIntrouvableException ex, HttpServletRequest requete) {
        return construire(HttpStatus.NOT_FOUND, "RESSOURCE_INTROUVABLE", ex.getMessage(), requete);
    }

    @ExceptionHandler(StockInsuffisantException.class)
    ProblemDetail gererStockInsuffisant(StockInsuffisantException ex, HttpServletRequest requete) {
        ProblemDetail probleme = construire(HttpStatus.CONFLICT, "STOCK_INSUFFISANT", ex.getMessage(), requete);
        probleme.setProperty("sku", ex.getSku());
        probleme.setProperty("stockDisponible", ex.getStockDisponible());
        return probleme;
    }

    @ExceptionHandler(RegleMetierException.class)
    ProblemDetail gererRegleMetier(RegleMetierException ex, HttpServletRequest requete) {
        return construire(HttpStatus.UNPROCESSABLE_CONTENT, ex.getCode(), ex.getMessage(), requete);
    }

    @ExceptionHandler(TropDeRequetesException.class)
    ResponseEntity<ProblemDetail> gererTropDeRequetes(TropDeRequetesException ex, HttpServletRequest requete) {
        long minutes = (ex.getReessayerDans() + 59) / 60;
        String delai = minutes <= 1 ? "une minute" : minutes + " minutes";
        ProblemDetail probleme = construire(HttpStatus.TOO_MANY_REQUESTS, "TROP_DE_REQUETES",
                "Trop de tentatives. Réessayez dans " + delai + ".", requete);
        probleme.setProperty("reessayerDans", ex.getReessayerDans());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getReessayerDans()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(probleme);
    }

    /** Filet de sécurité : jamais de pile d'appels ni de message technique renvoyé au client. */
    @ExceptionHandler(Exception.class)
    ProblemDetail gererErreurInattendue(Exception ex, HttpServletRequest requete) {
        log.error("Erreur inattendue sur {} {}", requete.getMethod(), requete.getRequestURI(), ex);
        return construire(HttpStatus.INTERNAL_SERVER_ERROR, "ERREUR_INTERNE", DETAILS_PAR_DEFAUT.get(500), requete);
    }

    // ---------------------------------------------------------------------
    // Validation (Bean Validation)
    // ---------------------------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders entetes, HttpStatusCode statut, WebRequest requete) {
        List<ErreurChamp> erreurs = ex.getBindingResult().getFieldErrors().stream()
                .map(erreur -> new ErreurChamp(erreur.getField(), messageOuDefaut(erreur)))
                .sorted(Comparator.comparing(ErreurChamp::champ))
                .toList();
        return reponseValidation(ex, erreurs, entetes, requete);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders entetes, HttpStatusCode statut, WebRequest requete) {
        List<ErreurChamp> erreurs = ex.getParameterValidationResults().stream()
                .flatMap(resultat -> resultat.getResolvableErrors().stream()
                        .map(erreur -> new ErreurChamp(
                                resultat.getMethodParameter().getParameterName(),
                                erreur.getDefaultMessage())))
                .toList();
        return reponseValidation(ex, erreurs, entetes, requete);
    }

    private ResponseEntity<Object> reponseValidation(
            Exception ex, List<ErreurChamp> erreurs, HttpHeaders entetes, WebRequest requete) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La requête contient des champs invalides.");
        probleme.setTitle("Données invalides");
        probleme.setProperty("code", "VALIDATION");
        probleme.setProperty("erreurs", erreurs);
        return handleExceptionInternal(ex, probleme, entetes, HttpStatus.BAD_REQUEST, requete);
    }

    private static String messageOuDefaut(FieldError erreur) {
        return erreur.getDefaultMessage() != null ? erreur.getDefaultMessage() : "Valeur invalide";
    }

    // ---------------------------------------------------------------------
    // Point de passage commun des erreurs gérées par Spring MVC
    // ---------------------------------------------------------------------

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object corps, HttpHeaders entetes, HttpStatusCode statut, WebRequest requete) {
        ResponseEntity<Object> reponse = super.handleExceptionInternal(ex, corps, entetes, statut, requete);
        if (reponse != null && reponse.getBody() instanceof ProblemDetail probleme) {
            boolean dejaTraduit = probleme.getProperties() != null && probleme.getProperties().containsKey("code");
            if (!dejaTraduit) {
                probleme.setTitle(ProblemesHttp.titre(statut));
                probleme.setDetail(DETAILS_PAR_DEFAUT.getOrDefault(statut.value(), probleme.getDetail()));
                probleme.setProperty("code", codeParDefaut(statut));
            }
            completer(probleme, cheminDe(requete));
        }
        return reponse;
    }

    // ---------------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------------

    private ProblemDetail construire(
            HttpStatus statut, String code, String detail, HttpServletRequest requete) {
        return problemes.creer(statut, code, detail, requete.getRequestURI());
    }

    private void completer(ProblemDetail probleme, @Nullable String chemin) {
        problemes.completer(probleme, chemin);
    }

    private static String codeParDefaut(HttpStatusCode statut) {
        HttpStatus connu = HttpStatus.resolve(statut.value());
        return connu != null ? connu.name() : "ERREUR_" + statut.value();
    }

    private static @Nullable String cheminDe(WebRequest requete) {
        return requete instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }
}
