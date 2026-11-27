package sn.boutique.commande.paiement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
 * sont jamais journalisés (données personnelles, clés). Un statut HTTP d'erreur n'est pas une exception : il est
 * renvoyé tel quel à l'appelant, qui décide.
 */
@Slf4j
@Component
public class ClientHttpPrestataire {

    /** Une réponse plus grosse est un signe d'anomalie : on ne la lit pas en entier. */
    private static final int TAILLE_MAX_REPONSE = 1_000_000;

    /** Réponse du prestataire. */
    public record Reponse(int statut, String corps) {

        public boolean reussie() {
            return statut >= 200 && statut < 300;
        }
    }

    private final ProprietesClientPaiement proprietes;
    private final RestClient client;

    @Autowired
    public ClientHttpPrestataire(ProprietesClientPaiement proprietes) {
        this(proprietes, RestClient.builder().requestFactory(fabrique(proprietes)));
    }

    /** Pour les tests de contrat : le constructeur de requêtes est lié à un faux serveur. */
    public ClientHttpPrestataire(ProprietesClientPaiement proprietes, RestClient.Builder constructeur) {
        this.proprietes = proprietes;
        this.client = constructeur.build();
    }

    private static JdkClientHttpRequestFactory fabrique(ProprietesClientPaiement proprietes) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(proprietes.delaiConnexion())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(http);
        fabrique.setReadTimeout(proprietes.delaiLecture());
        return fabrique;
    }

    /** POST d'un corps JSON, un seul essai. */
    public Reponse creer(String url, Map<String, String> entetes, String corpsJson) {
        return echanger(() -> client.post().uri(URI.create(url))
                .headers(h -> entetes.forEach(h::set))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(corpsJson)
                .exchange((requete, reponse) -> new Reponse(reponse.getStatusCode().value(), lire(reponse.getBody()))));
    }

    /** GET avec réessais limités. */
    public Reponse lire(String url, Map<String, String> entetes) {
        PaiementIndisponibleException derniere = null;
        for (int essai = 0; essai <= proprietes.reessaisLecture(); essai++) {
            if (essai > 0) {
                pause(proprietes.pauseReessai().toMillis() * essai);
            }
            try {
                Reponse reponse = echanger(() -> client.get().uri(URI.create(url))
                        .headers(h -> entetes.forEach(h::set))
                        .accept(MediaType.APPLICATION_JSON)
                        .exchange((requete, reponseHttp) ->
                                new Reponse(reponseHttp.getStatusCode().value(), lire(reponseHttp.getBody()))));
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

    private interface Appel {
        Reponse faire();
    }

    private Reponse echanger(Appel appel) {
        try {
            return appel.faire();
        } catch (RestClientException e) {
            if (delaiDepasse(e)) {
                throw new PaiementIndisponibleException("Le prestataire n'a pas répondu à temps");
            }
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new PaiementIndisponibleException("Le prestataire est injoignable : " + cause.getClass().getSimpleName());
        } catch (IllegalStateException e) {
            throw new PaiementIndisponibleException("Réponse du prestataire illisible : " + e.getMessage());
        }
    }

    private static boolean delaiDepasse(Throwable erreur) {
        for (Throwable t = erreur; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String lire(java.io.InputStream flux) {
        try {
            byte[] octets = flux.readNBytes(TAILLE_MAX_REPONSE + 1);
            if (octets.length > TAILLE_MAX_REPONSE) {
                throw new IllegalStateException("réponse trop volumineuse");
            }
            return new String(octets, StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("lecture impossible", e);
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
