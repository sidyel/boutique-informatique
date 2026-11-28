package sn.boutique.authentification.securite;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import sn.boutique.authentification.config.ProprietesSecondFacteur;

/**
 * Chiffre les secrets TOTP avant leur écriture en base : AES-256-GCM, un vecteur d'initialisation aléatoire par
 * chiffrement, et des <em>données associées</em> liées au compte (un secret copié d'un compte à l'autre ne se déchiffre pas).
 * <p>
 * Format : {@code v1.<identifiant de clé>.<IV>.<texte chiffré>} (Base64 URL). L'identifiant de clé permet de savoir quelle
 * clé a servi, donc de faire une rotation (ADR 0009 §3) : la clé courante chiffre, la précédente (facultative) ne sert qu'à lire.
 * <p>
 * <strong>Le démarrage échoue</strong> si la clé est absente ou trop courte : un secret écrit sans chiffrement fiable
 * rendrait le second facteur inutile en cas de fuite de la base.
 */
@Component
public class ChiffrementSecrets {

    static final int OCTETS_MINIMUM = 32;
    private static final int OCTETS_IV = 12;
    private static final int BITS_ETIQUETTE = 128;
    private static final String PREFIXE = "v1";
    private static final SecureRandom ALEATOIRE = new SecureRandom();

    private final String identifiantCourant;
    private final Map<String, SecretKeySpec> cles = new LinkedHashMap<>();

    public ChiffrementSecrets(ProprietesSecondFacteur proprietes) {
        SecretKeySpec courante = lireCle(proprietes.cleChiffrement(), "ADMIN_CLE_CHIFFREMENT", true);
        this.identifiantCourant = identifiant(courante);
        cles.put(identifiantCourant, courante);
        SecretKeySpec precedente = lireCle(proprietes.cleChiffrementPrecedente(), "ADMIN_CLE_CHIFFREMENT_PRECEDENTE", false);
        if (precedente != null) {
            cles.putIfAbsent(identifiant(precedente), precedente);
        }
    }

    public String chiffrer(String clair, long utilisateurId) {
        try {
            byte[] iv = new byte[OCTETS_IV];
            ALEATOIRE.nextBytes(iv);
            Cipher chiffre = Cipher.getInstance("AES/GCM/NoPadding");
            chiffre.init(Cipher.ENCRYPT_MODE, cles.get(identifiantCourant), new GCMParameterSpec(BITS_ETIQUETTE, iv));
            chiffre.updateAAD(donneesAssociees(utilisateurId));
            byte[] chiffres = chiffre.doFinal(clair.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
            return String.join(".", PREFIXE, identifiantCourant, base64.encodeToString(iv), base64.encodeToString(chiffres));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement du secret impossible", e);
        }
    }

    /** @throws IllegalStateException si la valeur est illisible, altérée, d'un autre compte ou chiffrée par une clé inconnue */
    public String dechiffrer(String valeur, long utilisateurId) {
        String[] parties = valeur == null ? new String[0] : valeur.split("\\.");
        if (parties.length != 4 || !PREFIXE.equals(parties[0])) {
            throw new IllegalStateException("Secret chiffré illisible");
        }
        SecretKeySpec cle = cles.get(parties[1]);
        if (cle == null) {
            throw new IllegalStateException("Clé de chiffrement inconnue (" + parties[1] + ") : rotation incomplète ?");
        }
        try {
            Base64.Decoder base64 = Base64.getUrlDecoder();
            Cipher chiffre = Cipher.getInstance("AES/GCM/NoPadding");
            chiffre.init(Cipher.DECRYPT_MODE, cle, new GCMParameterSpec(BITS_ETIQUETTE, base64.decode(parties[2])));
            chiffre.updateAAD(donneesAssociees(utilisateurId));
            return new String(chiffre.doFinal(base64.decode(parties[3])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Secret chiffré altéré ou appartenant à un autre compte", e);
        }
    }

    /** Vrai si la valeur est chiffrée par la clé courante (sinon : à réécrire lors d'une rotation). */
    public boolean estSousCleCourante(String valeur) {
        String[] parties = valeur == null ? new String[0] : valeur.split("\\.");
        return parties.length == 4 && identifiantCourant.equals(parties[1]);
    }

    public String identifiantCleCourante() {
        return identifiantCourant;
    }

    private static byte[] donneesAssociees(long utilisateurId) {
        return ("totp:" + utilisateurId).getBytes(StandardCharsets.UTF_8);
    }

    private static SecretKeySpec lireCle(String valeur, String variable, boolean obligatoire) {
        if (valeur == null || valeur.isBlank()) {
            if (obligatoire) {
                throw new IllegalStateException(variable + " est absente. Elle chiffre les secrets de la double "
                        + "authentification des comptes internes : générez-en une avec « openssl rand -base64 32 » et "
                        + "gardez-la hors de Git et hors de la base de données.");
            }
            return null;
        }
        byte[] octets;
        try {
            octets = Base64.getDecoder().decode(valeur.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(variable + " n'est pas du Base64 valide (« openssl rand -base64 32 »).");
        }
        if (octets.length < OCTETS_MINIMUM) {
            throw new IllegalStateException(variable + " est trop courte : " + octets.length + " octets, "
                    + OCTETS_MINIMUM + " au minimum (« openssl rand -base64 32 »).");
        }
        // La clé AES est le condensé SHA-256 des octets fournis : toute longueur au-delà du minimum est acceptée
        return new SecretKeySpec(sha256(octets), "AES");
    }

    private static String identifiant(SecretKeySpec cle) {
        return HexFormat.of().formatHex(sha256(cle.getEncoded()), 0, 4);
    }

    private static byte[] sha256(byte[] octets) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(octets);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
