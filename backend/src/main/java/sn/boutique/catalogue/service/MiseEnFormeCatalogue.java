package sn.boutique.catalogue.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import sn.boutique.catalogue.modele.TypeCaracteristique;
import sn.boutique.catalogue.modele.ValeurCaracteristique;

/** Mise en forme française des valeurs du catalogue (caractéristiques, réductions). */
public final class MiseEnFormeCatalogue {

    private MiseEnFormeCatalogue() {
    }

    /** Pourcentage de réduction arrondi à l'entier le plus proche, ou {@code null} sans réduction réelle. */
    public static Integer reduction(Long prix, Long prixBarre) {
        if (prix == null || prixBarre == null || prixBarre <= prix || prixBarre <= 0) {
            return null;
        }
        return BigDecimal.valueOf(prixBarre - prix).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(prixBarre), 0, RoundingMode.HALF_UP).intValue();
    }

    /** Valeur brute, telle qu'utilisée dans les filtres ({@code f.ecran_pouces=15.6}). */
    public static String valeurBrute(ValeurCaracteristique valeur) {
        if (valeur.getValeurNombre() != null) {
            return nombreBrut(valeur.getValeurNombre());
        }
        if (valeur.getValeurBooleen() != null) {
            return valeur.getValeurBooleen().toString();
        }
        return valeur.getValeurTexte();
    }

    /** Valeur affichée en français : « 15,6 », « Oui », texte inchangé. */
    public static String valeurAffichee(TypeCaracteristique type, String valeurBrute) {
        if (valeurBrute == null) {
            return null;
        }
        return switch (type) {
            case NOMBRE -> valeurBrute.replace('.', ',');
            case BOOLEEN -> Boolean.parseBoolean(valeurBrute) ? "Oui" : "Non";
            default -> valeurBrute;
        };
    }

    public static String nombreBrut(BigDecimal nombre) {
        return nombre.stripTrailingZeros().toPlainString();
    }
}
