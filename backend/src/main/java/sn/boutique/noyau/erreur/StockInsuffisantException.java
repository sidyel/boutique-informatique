package sn.boutique.noyau.erreur;

/**
 * Levée lorsque la quantité demandée dépasse le stock disponible d'une variante.
 * Traduite en HTTP 409.
 * <p>
 * Le stock exact n'est jamais exposé publiquement (information utile à la concurrence, voir
 * {@code Disponibilite}) : le message et la réponse ne donnent que la quantité commandable, plafonnée à
 * {@value #QUANTITE_MAX_AFFICHEE}. Sinon, demander 9 999 unités suffirait à lire le stock.
 */
public class StockInsuffisantException extends RuntimeException {

    /** Plafond de la quantité commandable annoncée au client. */
    public static final int QUANTITE_MAX_AFFICHEE = 10;

    private final String sku;
    private final int stockDisponible;

    public StockInsuffisantException(String sku, int quantiteDemandee, int stockDisponible) {
        super(message(sku, quantiteMaxCommandable(stockDisponible)));
        this.sku = sku;
        this.stockDisponible = stockDisponible;
    }

    private static int quantiteMaxCommandable(int stock) {
        return Math.clamp(stock, 0, QUANTITE_MAX_AFFICHEE);
    }

    private static String message(String sku, int quantiteMax) {
        return quantiteMax == 0
                ? "Le produit %s n'est plus disponible.".formatted(sku)
                : "Quantité indisponible pour %s : vous pouvez en commander %d au plus.".formatted(sku, quantiteMax);
    }

    /** Quantité commandable annoncée au client : min(stock, 10), jamais négative. */
    public int getQuantiteMaxCommandable() {
        return quantiteMaxCommandable(stockDisponible);
    }

    public String getSku() {
        return sku;
    }

    /** Stock exact : usage interne seulement (journaux du back-office), jamais dans une réponse publique. */
    public int getStockDisponible() {
        return stockDisponible;
    }
}
