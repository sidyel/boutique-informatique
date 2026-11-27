package sn.boutique.noyau.journal;

import java.util.regex.Pattern;

/**
 * Masque les jetons secrets d'une adresse avant de l'écrire dans un journal.
 * <p>
 * Un jeton de suivi de commande voyage dans le chemin ({@code /suivi/{jeton}},
 * {@code /api/commandes/suivi/{jeton}/…}), ceux des e-mails (vérification, réinitialisation, déverrouillage) dans le
 * paramètre {@code jeton}. Quiconque lit un journal ne doit pas pouvoir s'en servir : toute adresse écrite dans un
 * journal passe par {@link #adresse(String)}. Les mêmes règles s'appliquent au format de journal de nginx
 * (infra/nginx/default.conf.template).
 */
public final class MasqueJetons {

    public static final String MASQUE = "[jeton]";

    private static final Pattern CHEMIN_SUIVI = Pattern.compile("(/(?:suivi|retour)/)[^/?#]+");
    private static final Pattern PARAMETRE = Pattern.compile("([?&;](?:jeton|token)=)[^&#]*", Pattern.CASE_INSENSITIVE);

    private MasqueJetons() {
    }

    /** Chemin (avec ou sans paramètres) dont les jetons sont remplacés par {@value #MASQUE}. */
    public static String adresse(String adresse) {
        if (adresse == null) {
            return null;
        }
        String sansChemin = CHEMIN_SUIVI.matcher(adresse).replaceAll("$1" + MASQUE);
        return PARAMETRE.matcher(sansChemin).replaceAll("$1" + MASQUE);
    }
}
