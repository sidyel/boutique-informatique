package sn.boutique.noyau.erreur;

/**
 * Levée lorsque la quantité demandée dépasse le stock disponible d'une variante.
 * Traduite en HTTP 409.
 */
public class StockInsuffisantException extends RuntimeException {

    private final String sku;
    private final int stockDisponible;

    public StockInsuffisantException(String sku, int quantiteDemandee, int stockDisponible) {
        super("Stock insuffisant pour %s : %d demandé(s), %d disponible(s)."
                .formatted(sku, quantiteDemandee, stockDisponible));
        this.sku = sku;
        this.stockDisponible = stockDisponible;
    }

    public String getSku() {
        return sku;
    }

    public int getStockDisponible() {
        return stockDisponible;
    }
}
