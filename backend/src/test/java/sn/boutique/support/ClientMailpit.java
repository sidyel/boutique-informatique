package sn.boutique.support;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lit les e-mails reçus par le conteneur Mailpit de test (API REST de Mailpit).
 * Les e-mails partent en arrière-plan : les lectures attendent leur arrivée (10 s au plus).
 */
public class ClientMailpit {

    private static final Duration ATTENTE_MAX = Duration.ofSeconds(10);
    private static final Pattern JETON = Pattern.compile("jeton=([A-Za-z0-9_-]{43})");

    private final String url;
    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    public ClientMailpit(String url) {
        this.url = url;
    }

    /** E-mail reçu, tel que vu par le destinataire. */
    public record Email(String sujet, String expediteur, String html) {

        /** Jeton contenu dans le lien de l'e-mail. */
        public String jeton() {
            Matcher m = JETON.matcher(html);
            if (!m.find()) {
                throw new AssertionError("Aucun lien avec jeton dans l'e-mail « " + sujet + " »");
            }
            return m.group(1);
        }
    }

    /** Attend au moins {@code nombre} e-mails pour ce destinataire et les renvoie, du plus récent au plus ancien. */
    public List<Email> attendre(String destinataire, int nombre) {
        Instant limite = Instant.now().plus(ATTENTE_MAX);
        while (true) {
            JsonNode messages = rechercher(destinataire);
            if (messages.size() >= nombre) {
                List<Email> emails = new ArrayList<>();
                for (JsonNode resume : messages) {
                    JsonNode message = lire("/api/v1/message/" + resume.get("ID").asString());
                    emails.add(new Email(message.get("Subject").asString(),
                            message.get("From").get("Address").asString(), message.get("HTML").asString()));
                }
                return emails;
            }
            if (Instant.now().isAfter(limite)) {
                throw new AssertionError("%d e-mail(s) attendu(s) pour %s, %d reçu(s)"
                        .formatted(nombre, destinataire, messages.size()));
            }
            pause(Duration.ofMillis(150));
        }
    }

    public Email dernier(String destinataire) {
        return attendre(destinataire, 1).getFirst();
    }

    /** Nombre d'e-mails reçus après un court délai : sert à vérifier qu'AUCUN e-mail n'est parti. */
    public int compterApresDelai(String destinataire) {
        pause(Duration.ofMillis(1500));
        return rechercher(destinataire).size();
    }

    private JsonNode rechercher(String destinataire) {
        String requete = URLEncoder.encode("to:\"" + destinataire + "\"", StandardCharsets.UTF_8);
        return lire("/api/v1/search?query=" + requete).get("messages");
    }

    private JsonNode lire(String chemin) {
        try {
            HttpResponse<String> reponse = http.send(HttpRequest.newBuilder(URI.create(url + chemin)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return json.readTree(reponse.body());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void pause(Duration duree) {
        try {
            Thread.sleep(duree);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
