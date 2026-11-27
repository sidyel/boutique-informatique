package sn.boutique.commande.paiement;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import sn.boutique.commande.modele.TypeFournisseurPaiement;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PayDunya, API HTTP/JSON (documentation : developers.paydunya.com/doc/FR/http_json). Flux par redirection : la
 * facture est créée ({@code checkout-invoice/create}), le client est envoyé sur la page de paiement du prestataire,
 * puis le statut est relu serveur à serveur ({@code checkout-invoice/confirm/{token}}).
 * <p>
 * <strong>Points supposés, à vérifier au premier test réel</strong> (marqués {@code SUPPOSE} ci-dessous, listés dans
 * l'ADR 0008 et dans ENGAGEMENTS « Avant le lancement ») :
 * <ul>
 *   <li>SUPPOSE-1 : format d'un refus de création. La documentation dit seulement que {@code response_code "00"} est un
 *   succès ; tout autre code, ou une réponse HTTP non 2xx, est traité comme un échec.</li>
 *   <li>SUPPOSE-2 : la devise n'est pas dans la réponse de {@code confirm} ; XOF est supposée (compte sénégalais).</li>
 *   <li>SUPPOSE-3 : le numéro de commande est relu dans {@code custom_data.numero_commande} (la documentation dit que
 *   {@code custom_data} est renvoyé par {@code confirm}, sans en détailler la forme).</li>
 *   <li>SUPPOSE-4 : forme du jeton dans l'adresse de {@code confirm} (encodé comme un segment de chemin).</li>
 * </ul>
 * Aucun canal n'est imposé (le client choisit son moyen de paiement sur la page PayDunya) : une facture {@code pending}
 * reste payable jusqu'à 24 h, bien après l'échéance de 30 minutes de la commande ; ce paiement tardif est géré par
 * {@link ConfirmationPaiementService} et {@link ReceptionPaiementService}.
 */
@Slf4j
@Component
public class PaydunyaFournisseur implements FournisseurPaiement {

    static final String DEVISE = "XOF";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ProprietesPaydunya proprietes;
    private final ClientHttpPrestataire client;

    public PaydunyaFournisseur(ProprietesPaydunya proprietes, ClientHttpPrestataire client) {
        this.proprietes = proprietes;
        this.client = client;
    }

    @Override
    public TypeFournisseurPaiement type() {
        return TypeFournisseurPaiement.PAYDUNYA;
    }

    /** Sans les trois clés, le mobile money est masqué. */
    @Override
    public boolean disponible() {
        return proprietes.configure();
    }

    @Override
    public SessionPaiement initier(DemandePaiement demande) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("invoice", Map.of(
                "total_amount", demande.montant(),
                "description", "Commande " + demande.numeroCommande()));
        corps.put("store", Map.of("name", proprietes.nomBoutique()));
        corps.put("custom_data", Map.of("numero_commande", demande.numeroCommande()));
        Map<String, String> actions = new HashMap<>();
        actions.put("return_url", demande.urlRetour());
        actions.put("cancel_url", demande.urlAnnulation());
        actions.put("callback_url", demande.urlNotification());
        corps.put("actions", actions);

        ClientHttpPrestataire.Reponse reponse = client.creer(proprietes.urlBase() + "checkout-invoice/create",
                entetes(), JSON.writeValueAsString(corps));
        JsonNode json = lireJson(reponse);
        String code = json.path("response_code").asString("");
        String token = json.path("token").asString("");
        String url = json.path("response_text").asString("");
        if (!reponse.reussie() || !"00".equals(code) || token.isBlank() || !url.startsWith("https://")) {
            // SUPPOSE-1 : on ne connaît pas le format exact d'un refus ; le texte du prestataire va dans le journal,
            // jamais les clés ni la facture
            log.warn("PayDunya a refusé la création de la facture de la commande {} : HTTP {}, response_code={}, "
                            + "response_text={}", demande.numeroCommande(), reponse.statut(), code, "00".equals(code) ? "(adresse)" : tronquer(url));
            throw new PaiementIndisponibleException("PayDunya a refusé la création de la facture");
        }
        return new SessionPaiement(token, url);
    }

    @Override
    public ResultatVerification verifier(String referenceExterne) {
        // SUPPOSE-4 : le jeton est un segment de chemin
        String url = proprietes.urlBase() + "checkout-invoice/confirm/"
                + UriUtils.encodePathSegment(referenceExterne, StandardCharsets.UTF_8);
        ClientHttpPrestataire.Reponse reponse = client.lire(url, entetes());
        JsonNode json = lireJson(reponse);
        if (!reponse.reussie()) {
            log.warn("PayDunya : vérification d'une facture refusée, HTTP {}", reponse.statut());
            throw new PaiementIndisponibleException("PayDunya n'a pas pu vérifier la facture (HTTP " + reponse.statut() + ")");
        }
        Statut statut = statut(json.path("status").asString(""));
        long montant = montant(json.path("invoice").path("total_amount"));
        // SUPPOSE-3
        String numero = json.path("custom_data").path("numero_commande").asString(null);
        // SUPPOSE-2
        return new ResultatVerification(statut, montant, DEVISE, numero);
    }

    /**
     * Le {@code hash} d'une notification est le SHA-512 de la clé principale. Il est <strong>fixe</strong> : il prouve
     * seulement que l'expéditeur connaît la clé, il ne signe pas le contenu. Une notification authentique n'est donc
     * jamais crue sur parole : elle déclenche une vérification serveur à serveur.
     * Comparaison en temps constant.
     */
    public boolean notificationAuthentique(String hashRecu) {
        if (!proprietes.configure() || hashRecu == null || hashRecu.isBlank()) {
            return false;
        }
        byte[] attendu = sha512(proprietes.cleMaitre()).getBytes(StandardCharsets.US_ASCII);
        byte[] recu = hashRecu.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(attendu, recu);
    }

    private Map<String, String> entetes() {
        return Map.of("PAYDUNYA-MASTER-KEY", proprietes.cleMaitre(), "PAYDUNYA-PRIVATE-KEY", proprietes.clePrivee(),
                "PAYDUNYA-TOKEN", proprietes.jeton());
    }

    /** pending, completed, cancelled, failed : la casse varie dans la documentation (PENDING, COMPLETED…). */
    static Statut statut(String valeur) {
        return switch (valeur.strip().toLowerCase(Locale.ROOT)) {
            case "pending" -> Statut.EN_ATTENTE;
            case "completed" -> Statut.REUSSI;
            case "cancelled", "canceled" -> Statut.ANNULE;
            case "failed" -> Statut.ECHOUE;
            // Statut inconnu : on ne décide rien (rien n'est modifié, la vérification sera refaite)
            default -> throw new PaiementIndisponibleException("Statut de facture PayDunya inconnu : " + tronquer(valeur));
        };
    }

    /** -1 si le montant est absent ou n'est pas un entier : la vérification le déclare incohérent. */
    private static long montant(JsonNode noeud) {
        try {
            return new BigDecimal(noeud.asString("").strip()).setScale(0, java.math.RoundingMode.UNNECESSARY).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }

    private static JsonNode lireJson(ClientHttpPrestataire.Reponse reponse) {
        try {
            JsonNode json = JSON.readTree(reponse.corps());
            if (json == null || !json.isObject()) {
                throw new PaiementIndisponibleException("Réponse de PayDunya illisible (HTTP " + reponse.statut() + ")");
            }
            return json;
        } catch (tools.jackson.core.JacksonException e) {
            throw new PaiementIndisponibleException("Réponse de PayDunya illisible (HTTP " + reponse.statut() + ")");
        }
    }

    private static String sha512(String texte) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(texte.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tronquer(String texte) {
        String propre = texte.replaceAll("[\\r\\n]", " ");
        return propre.length() > 200 ? propre.substring(0, 200) + "…" : propre;
    }
}
