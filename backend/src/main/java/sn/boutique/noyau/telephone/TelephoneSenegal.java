package sn.boutique.noyau.telephone;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalisation des numéros de téléphone sénégalais au format E.164 : {@code +221XXXXXXXXX}.
 * <p>
 * Ce format unique sert partout : enregistrement (Utilisateur, Adresse, Commande), liens WhatsApp
 * ({@code wa.me/221…}) et hachage SHA-256 pour l'API Conversions de Meta (phase 7).
 * <p>
 * Numéros acceptés (9 chiffres nationaux) : mobiles 70, 75, 76, 77, 78 et fixes 33.
 * Saisies tolérées : « 77 123 45 67 », « 77.123.45.67 », « +221 77 123 45 67 »,
 * « 00221771234567 », « 221771234567 », « (+221) 77-123-45-67 ».
 */
public final class TelephoneSenegal {

    public static final String INDICATIF = "+221";

    /** Préfixes autorisés des numéros nationaux à 9 chiffres. */
    public static final Set<String> PREFIXES_AUTORISES = Set.of("70", "75", "76", "77", "78", "33");

    /** Format stocké en base (même règle que la contrainte CHECK des migrations). */
    public static final Pattern FORMAT_E164 = Pattern.compile("^\\+221(70|75|76|77|78|33)[0-9]{7}$");

    private static final Pattern SEPARATEURS = Pattern.compile("[\\s.\\-()/]");

    private TelephoneSenegal() {
    }

    /**
     * Normalise une saisie au format E.164.
     *
     * @return le numéro normalisé, ou vide si la saisie n'est pas un numéro sénégalais valide
     */
    public static Optional<String> normaliser(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return Optional.empty();
        }
        String chiffres = SEPARATEURS.matcher(saisie.strip()).replaceAll("");

        if (chiffres.startsWith("+221")) {
            chiffres = chiffres.substring(4);
        } else if (chiffres.startsWith("00221")) {
            chiffres = chiffres.substring(5);
        } else if (chiffres.startsWith("221") && chiffres.length() == 12) {
            chiffres = chiffres.substring(3);
        }

        if (chiffres.length() != 9 || !chiffres.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        if (!PREFIXES_AUTORISES.contains(chiffres.substring(0, 2))) {
            return Optional.empty();
        }
        return Optional.of(INDICATIF + chiffres);
    }

    /**
     * Normalise une saisie obligatoirement valide (usage dans les entités).
     *
     * @throws TelephoneInvalideException si la saisie n'est pas un numéro sénégalais valide
     */
    public static String normaliserOuEchouer(String saisie) {
        return normaliser(saisie).orElseThrow(() -> new TelephoneInvalideException(saisie));
    }

    /** Variante pour les champs facultatifs : null ou vide donne null. */
    public static String normaliserFacultatif(String saisie) {
        return saisie == null || saisie.isBlank() ? null : normaliserOuEchouer(saisie);
    }

    public static boolean estValide(String saisie) {
        return normaliser(saisie).isPresent();
    }

    /** Numéro pour les liens WhatsApp : {@code 221771234567} (sans le « + »). */
    public static String pourWhatsApp(String numeroE164) {
        return numeroE164.substring(1);
    }
}
