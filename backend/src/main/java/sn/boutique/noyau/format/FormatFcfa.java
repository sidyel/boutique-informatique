package sn.boutique.noyau.format;

/** Montant en FCFA comme sur le site : « 1 250 000 FCFA » (espaces insécables, aucune décimale). */
public final class FormatFcfa {

    private FormatFcfa() {
    }

    public static String formater(long montant) {
        String chiffres = Long.toString(Math.abs(montant));
        StringBuilder resultat = new StringBuilder(montant < 0 ? "−" : "");
        for (int i = 0; i < chiffres.length(); i++) {
            if (i > 0 && (chiffres.length() - i) % 3 == 0) {
                resultat.append(' ');
            }
            resultat.append(chiffres.charAt(i));
        }
        return resultat.append(" FCFA").toString();
    }
}
