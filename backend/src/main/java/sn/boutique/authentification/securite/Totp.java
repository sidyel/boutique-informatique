package sn.boutique.authentification.securite;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mots de passe à usage unique basés sur le temps (TOTP, RFC 6238) avec HMAC-SHA1, le seul algorithme que toutes les
 * applications d'authentification acceptent. Aucune bibliothèque : le JDK suffit (ADR 0009). Les vecteurs de test de la
 * RFC 6238 (annexe B) sont vérifiés par {@code TotpTest}.
 */
public final class Totp {

    public static final int PAS_SECONDES = 30;
    public static final int CHIFFRES = 6;
    /** Pas voisins acceptés de chaque côté du pas courant : dérive de l'horloge du téléphone (±30 s). */
    public static final int FENETRE = 1;
    private static final int OCTETS_SECRET = 20;
    private static final SecureRandom ALEATOIRE = new SecureRandom();

    private Totp() {
    }

    /** Secret de 160 bits (la taille recommandée pour HMAC-SHA1), encodé en Base32. */
    public static String genererSecret() {
        byte[] secret = new byte[OCTETS_SECRET];
        ALEATOIRE.nextBytes(secret);
        return Base32.encoder(secret);
    }

    /** Numéro du pas de 30 secondes qui contient l'instant (compteur de la RFC 6238). */
    public static long pas(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), PAS_SECONDES);
    }

    /** Code du pas donné, sur {@code chiffres} chiffres (RFC 4226, troncature dynamique). */
    public static String code(byte[] secret, long pas, int chiffres) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] condense = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(pas).array());
            int decalage = condense[condense.length - 1] & 0x0F;
            int binaire = ((condense[decalage] & 0x7F) << 24)
                    | ((condense[decalage + 1] & 0xFF) << 16)
                    | ((condense[decalage + 2] & 0xFF) << 8)
                    | (condense[decalage + 3] & 0xFF);
            int modulo = 1;
            for (int i = 0; i < chiffres; i++) {
                modulo *= 10;
            }
            return String.format("%0" + chiffres + "d", binaire % modulo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 indisponible", e);
        }
    }

    /**
     * Cherche le pas dont le code est celui saisi, parmi le pas courant et ses voisins. Comparaison en temps constant.
     *
     * @return le pas correspondant (à mémoriser contre le rejeu), ou vide si le code est faux ou mal formé
     */
    public static OptionalLong verifier(byte[] secret, String saisi, Instant maintenant) {
        String propre = normaliser(saisi);
        if (propre == null) {
            return OptionalLong.empty();
        }
        long courant = pas(maintenant);
        long trouve = -1;
        for (long pas = courant - FENETRE; pas <= courant + FENETRE; pas++) {
            boolean egal = MessageDigest.isEqual(code(secret, pas, CHIFFRES).getBytes(StandardCharsets.US_ASCII),
                    propre.getBytes(StandardCharsets.US_ASCII));
            if (egal) {
                trouve = pas;
            }
        }
        return trouve < 0 ? OptionalLong.empty() : OptionalLong.of(trouve);
    }

    /** Six chiffres exactement (espaces tolérés : « 123 456 »), sinon {@code null}. */
    public static String normaliser(String saisi) {
        if (saisi == null) {
            return null;
        }
        String propre = saisi.replaceAll("\\s", "");
        return propre.matches("\\d{" + CHIFFRES + "}") ? propre : null;
    }

    /** Adresse {@code otpauth://} lue par les applications d'authentification (QR code). */
    public static String uri(String emetteur, String compte, String secretBase32) {
        String etiquette = encoder(emetteur) + ":" + encoder(compte);
        return "otpauth://totp/" + etiquette + "?secret=" + secretBase32 + "&issuer=" + encoder(emetteur)
                + "&algorithm=SHA1&digits=" + CHIFFRES + "&period=" + PAS_SECONDES;
    }

    private static String encoder(String texte) {
        return URLEncoder.encode(texte, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
