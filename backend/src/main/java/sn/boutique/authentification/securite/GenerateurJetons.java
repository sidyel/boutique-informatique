package sn.boutique.authentification.securite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Jetons opaques (rafraîchissement, vérification d'e-mail, réinitialisation du mot de passe) :
 * 256 bits aléatoires encodés en Base64 URL. Seule leur empreinte SHA-256 est stockée en base.
 */
public final class GenerateurJetons {

    private static final SecureRandom ALEATOIRE = new SecureRandom();
    private static final int OCTETS = 32;

    private GenerateurJetons() {
    }

    public static String generer() {
        byte[] octets = new byte[OCTETS];
        ALEATOIRE.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    /** SHA-256 en hexadécimal minuscule (64 caractères), format imposé par les contraintes CHECK de la base. */
    public static String empreinte(String jeton) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
