package sn.boutique.authentification.securite;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/**
 * Base32 de la RFC 4648 (alphabet A-Z et 2-7), sans remplissage à l'écriture : c'est le format de secret que les
 * applications d'authentification attendent. À la lecture, les espaces, tirets, remplissage et minuscules sont tolérés
 * (un secret recopié à la main).
 */
public final class Base32 {

    static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {
    }

    public static String encoder(byte[] octets) {
        StringBuilder sortie = new StringBuilder((octets.length * 8 + 4) / 5);
        int tampon = 0;
        int bits = 0;
        for (byte octet : octets) {
            tampon = (tampon << 8) | (octet & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sortie.append(ALPHABET.charAt((tampon >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sortie.append(ALPHABET.charAt((tampon << (5 - bits)) & 0x1F));
        }
        return sortie.toString();
    }

    /** @throws IllegalArgumentException si le texte contient un caractère hors de l'alphabet */
    public static byte[] decoder(String texte) {
        String propre = texte.replaceAll("[\\s-]", "").replace("=", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream(propre.length() * 5 / 8);
        int tampon = 0;
        int bits = 0;
        for (int i = 0; i < propre.length(); i++) {
            int valeur = ALPHABET.indexOf(propre.charAt(i));
            if (valeur < 0) {
                throw new IllegalArgumentException("Caractère Base32 invalide : " + propre.charAt(i));
            }
            tampon = (tampon << 5) | valeur;
            bits += 5;
            if (bits >= 8) {
                sortie.write((tampon >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return sortie.toByteArray();
    }
}
