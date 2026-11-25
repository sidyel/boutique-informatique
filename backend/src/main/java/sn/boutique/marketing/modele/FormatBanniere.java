package sn.boutique.marketing.modele;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Formats conseillés des images de bannière (ADR 0005). Les proportions ne bloquent pas l'envoi : une image
 * qui s'en éloigne beaucoup reçoit un avertissement (elle sera fortement recadrée à l'affichage).
 */
public enum FormatBanniere {

    /** Bandeau large : 1920 × 600 (3,2:1), 1440 px de large au moins pour rester net. */
    ORDINATEUR(1920, 600, 1440),
    /** Portrait : 800 × 1000 (4:5), 640 px de large au moins. */
    MOBILE(800, 1000, 640);

    /** Écart de proportions toléré sans avertissement (25 %). */
    static final double ECART_TOLERE = 0.25;

    private final int largeurConseillee;
    private final int hauteurConseillee;
    private final int largeurMinConseillee;

    FormatBanniere(int largeurConseillee, int hauteurConseillee, int largeurMinConseillee) {
        this.largeurConseillee = largeurConseillee;
        this.hauteurConseillee = hauteurConseillee;
        this.largeurMinConseillee = largeurMinConseillee;
    }

    public String libelle() {
        return largeurConseillee + " × " + hauteurConseillee + " px";
    }

    /** Avertissements pour une image de ces dimensions (liste vide si elle convient). */
    public List<String> avertissements(int largeur, int hauteur) {
        List<String> avertissements = new ArrayList<>();
        double proportions = (double) largeur / hauteur;
        double conseillees = (double) largeurConseillee / hauteurConseillee;
        if (Math.abs(proportions / conseillees - 1) > ECART_TOLERE) {
            avertissements.add(String.format(Locale.FRANCE,
                    "Proportions %.2f:1 éloignées du format conseillé %s (%.2f:1) : l'image sera fortement recadrée.",
                    proportions, libelle(), conseillees));
        }
        if (largeur < largeurMinConseillee) {
            avertissements.add("Image de %d px de large : au moins %d px conseillés (%s idéalement) pour rester nette."
                    .formatted(largeur, largeurMinConseillee, libelle()));
        }
        return avertissements;
    }
}
