package sn.boutique.authentification.securite;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Codes de secours : dix codes {@code XXXXX-XXXXX} de 50 bits, alphabet sans caractères ambigus (ni 0/O, ni 1/I/L) pour
 * qu'on puisse les recopier depuis une feuille imprimée. Seule l'empreinte SHA-256 du code normalisé est stockée.
 */
public final class GenerateurCodesSecours {

    public static final int NOMBRE = 10;
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LONGUEUR = 10;
    private static final SecureRandom ALEATOIRE = new SecureRandom();

    private GenerateurCodesSecours() {
    }

    public static List<String> generer() {
        List<String> codes = new ArrayList<>(NOMBRE);
        for (int i = 0; i < NOMBRE; i++) {
            StringBuilder code = new StringBuilder(LONGUEUR + 1);
            for (int j = 0; j < LONGUEUR; j++) {
                if (j == LONGUEUR / 2) {
                    code.append('-');
                }
                code.append(ALPHABET.charAt(ALEATOIRE.nextInt(ALPHABET.length())));
            }
            codes.add(code.toString());
        }
        return codes;
    }

    /** Majuscules, sans espaces ni tirets : « abcde fghjk » et « ABCDE-FGHJK » donnent la même empreinte. */
    public static String normaliser(String saisi) {
        return saisi == null ? "" : saisi.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean bienForme(String saisi) {
        String propre = normaliser(saisi);
        if (propre.length() != LONGUEUR) {
            return false;
        }
        for (int i = 0; i < propre.length(); i++) {
            if (ALPHABET.indexOf(propre.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    public static String empreinte(String saisi) {
        return GenerateurJetons.empreinte(normaliser(saisi));
    }
}
