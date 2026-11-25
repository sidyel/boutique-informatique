package sn.boutique.media;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sn.boutique.noyau.erreur.RegleMetierException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Vérification par oEmbed ({@code https://www.youtube.com/oembed}) : 200 pour une vidéo publique intégrable,
 * 401/403 pour une vidéo privée ou dont l'auteur a désactivé l'intégration, 404 (ou 400) pour une vidéo
 * supprimée ou inexistante. Délai court : l'administrateur attend la réponse.
 */
@Slf4j
@Component
public class VerificationYoutubeOembed implements VerificationYoutube {

    private final ProprietesMedias.Youtube proprietes;
    private final JsonMapper jsonMapper;
    private final HttpClient client;

    public VerificationYoutubeOembed(ProprietesMedias proprietes, JsonMapper jsonMapper) {
        this.proprietes = proprietes.youtube();
        this.jsonMapper = jsonMapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(this.proprietes.delai())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public VideoYoutube verifier(String identifiant) {
        String lien = LienYoutube.urlCanonique(identifiant);
        URI uri = URI.create(proprietes.urlOembed() + "?format=json&url=" + URLEncoder.encode(lien, StandardCharsets.UTF_8));
        HttpRequest requete = HttpRequest.newBuilder(uri).timeout(proprietes.delai()).GET().build();
        HttpResponse<String> reponse;
        try {
            reponse = client.send(requete, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new YoutubeIndisponibleException("YouTube n'a pas répondu en " + proprietes.delai().toSeconds()
                    + " s (" + identifiant + ")", e);
        } catch (IOException e) {
            throw new YoutubeIndisponibleException("YouTube injoignable (" + identifiant + ") : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YoutubeIndisponibleException("Vérification YouTube interrompue (" + identifiant + ")", e);
        }

        return switch (reponse.statusCode()) {
            case 200 -> lire(identifiant, reponse.body());
            case 401, 403 -> throw new RegleMetierException("YOUTUBE_NON_INTEGRABLE",
                    "Cette vidéo YouTube est privée ou son auteur a désactivé l'intégration sur d'autres sites : "
                            + "elle ne peut pas être affichée sur la fiche produit.");
            case 400, 404 -> throw new RegleMetierException("YOUTUBE_INTROUVABLE",
                    "Cette vidéo YouTube n'existe pas ou a été supprimée.");
            default -> throw new YoutubeIndisponibleException("YouTube a répondu " + reponse.statusCode()
                    + " (" + identifiant + ")", null);
        };
    }

    private VideoYoutube lire(String identifiant, String corps) {
        try {
            JsonNode json = jsonMapper.readTree(corps);
            return new VideoYoutube(identifiant, texte(json, "title"), texte(json, "author_name"));
        } catch (RuntimeException e) {
            throw new YoutubeIndisponibleException("Réponse oEmbed illisible (" + identifiant + ")", e);
        }
    }

    private static String texte(JsonNode json, String champ) {
        JsonNode valeur = json.get(champ);
        return valeur == null || valeur.isNull() ? null : valeur.asString();
    }
}
