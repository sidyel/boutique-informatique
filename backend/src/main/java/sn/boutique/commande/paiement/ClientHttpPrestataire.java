package sn.boutique.commande.paiement;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Client HTTP vers un prestataire de paiement : délais courts et réessais strictement encadrés.
 * <ul>
 *   <li><strong>Création</strong> ({@link #creer}) : un seul essai. Un réessai après un délai dépassé pourrait créer une
 *   seconde facture chez le prestataire (la première a peut-être été créée) : on préfère échouer, le client
 *   « reprend le paiement » ensuite.</li>
 *   <li><strong>Lecture</strong> ({@link #lire}) : réessais limités ({@code reessaisLecture}) sur un échec réseau, un
 *   délai dépassé ou une erreur 5xx. Une réponse 4xx n'est jamais réessayée.</li>
 * </ul>
 * Toute défaillance devient une {@link PaiementIndisponibleException} : le reste du site continue de répondre, et
 * l'appelant n'est jamais dans une transaction (voir {@link PaiementService}). Les corps de requête et de réponse ne
 * sont jamais journalisés (données personnelles, clés).
 */
@Slf4j
@Component
public class ClientHttpPrestataire {

    /** Réponse du prestataire. */
    public record Reponse(int statut, String corps) {

        public boolean reussie() {
            return statut >= 200 && statut < 300;
        }
    }

    private final ProprietesClientPaiement proprietes;
    private final HttpClient client;

    public ClientHttpPrestataire(ProprietesClientPaiement proprietes) {
        this.proprietes = proprietes;
        this.client = HttpClient.newBuilder()
                .connectTimeout(proprietes.delaiConnexion())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** POST d'un corps JSON, un seul essai. */
    public Reponse creer(String url, Map<String, String> entetes, String corpsJson) {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create(url))
                .timeout(proprietes.delaiLecture())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpsJson));
        entetes.forEach(requete::header);
        return envoyer(requete.build());
    }

    /** GET avec réessais limités. */
    public Reponse lire(String url, Map<String, String> entetes) {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create(url))
                .timeout(proprietes.delaiLecture())
                .header("Accept", "application/json")
                .GET();
        entetes.forEach(requete::header);
        HttpRequest construite = requete.build();
        PaiementIndisponibleException derniere = null;
        for (int essai = 0; essai <= proprietes.reessaisLecture(); essai++) {
            if (essai > 0) {
                pause(proprietes.pauseReessai().toMillis() * essai);
            }
            try {
                Reponse reponse = envoyer(construite);
                if (reponse.statut() < 500) {
                    return reponse;
                }
                derniere = new PaiementIndisponibleException("Le prestataire a répondu " + reponse.statut());
            } catch (PaiementIndisponibleException e) {
                derniere = e;
            }
            log.warn("Lecture chez le prestataire échouée (essai {}/{}) : {}", essai + 1,
                    proprietes.reessaisLecture() + 1, derniere.getMessage());
        }
        throw derniere;
    }

    /** Un échange complet (connexion, envoi, réponse) borné dans le temps ; une erreur 5xx est renvoyée telle quelle. */
    private Reponse envoyer(HttpRequest requete) {
        long totalMs = proprietes.delaiConnexion().toMillis() + proprietes.delaiLecture().toMillis();
        var futur = client.sendAsync(requete, HttpResponse.BodyHandlers.ofString());
        try {
            HttpResponse<String> reponse = futur.get(totalMs, TimeUnit.MILLISECONDS);
            return new Reponse(reponse.statusCode(), reponse.body());
        } catch (TimeoutException e) {
            futur.cancel(true);
            throw new PaiementIndisponibleException("Le prestataire n'a pas répondu à temps (" + totalMs + " ms)");
        } catch (ExecutionException e) {
            futur.cancel(true);
            Throwable cause = e.getCause();
            throw new PaiementIndisponibleException("Le prestataire est injoignable : "
                    + (cause instanceof IOException ? cause.getClass().getSimpleName() : String.valueOf(cause)));
        } catch (InterruptedException e) {
            futur.cancel(true);
            Thread.currentThread().interrupt();
            throw new PaiementIndisponibleException("Appel au prestataire interrompu");
        }
    }

    private static void pause(long millisecondes) {
        try {
            Thread.sleep(millisecondes);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaiementIndisponibleException("Appel au prestataire interrompu");
        }
    }
}
