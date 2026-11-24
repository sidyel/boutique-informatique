package sn.boutique.noyau.erreur;

import java.net.URI;
import java.time.Clock;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Fabrique commune des réponses d'erreur ProblemDetail (RFC 9457) : utilisée par le gestionnaire global,
 * les filtres de sécurité (qui interviennent avant Spring MVC) et les contrôleurs.
 * Propriétés ajoutées : {@code code} (identifiant stable pour le frontend) et {@code horodatage}.
 */
@Component
public class ProblemesHttp {

    static final Map<Integer, String> TITRES = Map.ofEntries(
            Map.entry(400, "Requête invalide"),
            Map.entry(401, "Authentification requise"),
            Map.entry(403, "Accès refusé"),
            Map.entry(404, "Ressource introuvable"),
            Map.entry(405, "Méthode non autorisée"),
            Map.entry(406, "Format de réponse non disponible"),
            Map.entry(409, "Conflit"),
            Map.entry(413, "Contenu trop volumineux"),
            Map.entry(415, "Type de contenu non pris en charge"),
            Map.entry(422, "Règle métier non respectée"),
            Map.entry(429, "Trop de requêtes"),
            Map.entry(500, "Erreur interne"),
            Map.entry(503, "Service indisponible"));

    private final Clock horloge;

    public ProblemesHttp(Clock horloge) {
        this.horloge = horloge;
    }

    public ProblemDetail creer(HttpStatus statut, String code, String detail, @Nullable String chemin) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(statut, detail);
        probleme.setTitle(titre(statut));
        probleme.setProperty("code", code);
        completer(probleme, chemin);
        return probleme;
    }

    public static String titre(HttpStatusCode statut) {
        return TITRES.getOrDefault(statut.value(), HttpStatus.resolve(statut.value()) != null
                ? HttpStatus.resolve(statut.value()).getReasonPhrase() : "Erreur");
    }

    public void completer(ProblemDetail probleme, @Nullable String chemin) {
        if (probleme.getInstance() == null && chemin != null) {
            probleme.setInstance(URI.create(chemin));
        }
        probleme.setProperty("horodatage", horloge.instant());
    }
}
