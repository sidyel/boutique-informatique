package sn.boutique.catalogue.validation;

import java.util.List;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Règles de {@link TexteAlternatif}. */
public class TexteAlternatifValidateur implements ConstraintValidator<TexteAlternatif, String> {

    public static final int LONGUEUR_MIN = 5;
    public static final int LONGUEUR_MAX = 150;

    private static final List<Pattern> NOMS_DE_FICHIER = List.of(
            // Se termine par une extension d'image ou de vidéo : « photo.png », « vivobook face.JPG »
            Pattern.compile("(?i).*\\.(jpe?g|jfif|png|webp|gif|heic|heif|avif|bmp|tiff?|svg|raw|cr2|nef|mp4|mov|webm)$"),
            // Appareils photo et téléphones, sans espace : IMG_1234, DSC0001, PXL_20240101_143022, P1000123
            Pattern.compile("(?i)^(img|dsc[nf]?|dcim|pxl|mvimg|vid|gopr|gp|wp|sam|photo|image|picture|pic|p)"
                    + "[_-]?\\d{2,}([_.-][\\w.-]*)?$"),
            // Captures et messageries, avec leur date : « Screenshot_2024… », « Capture d'écran 2025-… »,
            // « WhatsApp Image 2024-… »
            Pattern.compile("(?i)^(screenshot[ _-]?|screen shot |capture d.\\s?[ée]cran |whatsapp (image|video) )"
                    + "\\d{4}.*"),
            // Uniquement des chiffres et séparateurs : « 20240101_143022 », « 1234-5678 »
            Pattern.compile("^[\\d _.:-]+$"),
            // Identifiant technique : longue suite hexadécimale ou UUID
            Pattern.compile("(?i)^[0-9a-f-]{16,}$"),
            // Un seul « mot » qui mêle lettres, chiffres et _ : « produit_final_v2 », « asus_tuf_a15 »
            Pattern.compile("^[\\p{L}\\d]+(_[\\p{L}\\d]+)+$"));

    @Override
    public boolean isValid(String valeur, ConstraintValidatorContext contexte) {
        // Convention Bean Validation : null est accepté ici, @NotNull le rend obligatoire
        return valeur == null || estValide(valeur);
    }

    public static boolean estValide(String valeur) {
        if (valeur == null) {
            return false;
        }
        String texte = valeur.strip();
        return texte.length() >= LONGUEUR_MIN && texte.length() <= LONGUEUR_MAX && !ressembleANomDeFichier(texte);
    }

    public static boolean ressembleANomDeFichier(String texte) {
        String nettoye = texte.strip();
        return NOMS_DE_FICHIER.stream().anyMatch(motif -> motif.matcher(nettoye).matches());
    }

    /** Suggestion par défaut : « nom du produit – libellé de la variante », coupée proprement à 150 caractères. */
    public static String suggestion(String nomProduit, String libelleVariante) {
        String texte = libelleVariante == null || libelleVariante.isBlank()
                ? nomProduit.strip()
                : nomProduit.strip() + " – " + libelleVariante.strip();
        if (texte.length() <= LONGUEUR_MAX) {
            return texte;
        }
        String coupe = texte.substring(0, LONGUEUR_MAX);
        int espace = coupe.lastIndexOf(' ');
        return (espace > LONGUEUR_MAX / 2 ? coupe.substring(0, espace) : coupe).strip();
    }
}
