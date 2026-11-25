package sn.boutique.catalogue.dto;

/**
 * Disponibilité affichée. Le stock exact n'est jamais exposé publiquement (information utile à la concurrence).
 */
public enum Disponibilite {
    EN_STOCK,
    /** Stock inférieur ou égal au seuil d'alerte de la variante : « Plus que quelques exemplaires ». */
    STOCK_FAIBLE,
    RUPTURE;

    public static Disponibilite depuisStock(int stock, int seuilAlerte) {
        if (stock <= 0) {
            return RUPTURE;
        }
        return stock <= seuilAlerte ? STOCK_FAIBLE : EN_STOCK;
    }

    /** La meilleure disponibilité parmi celles des variantes. */
    public Disponibilite meilleure(Disponibilite autre) {
        return autre != null && autre.ordinal() < ordinal() ? autre : this;
    }
}
