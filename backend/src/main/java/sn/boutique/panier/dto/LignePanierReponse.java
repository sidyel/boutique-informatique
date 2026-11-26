package sn.boutique.panier.dto;

import sn.boutique.catalogue.dto.Disponibilite;
import sn.boutique.catalogue.dto.ImageReponse;

/**
 * Ligne du panier. Le stock exact n'est jamais exposé : {@code quantiteMax} est plafonnée par la limite par
 * ligne (5 par défaut) et par {@code StockInsuffisantException.QUANTITE_MAX_AFFICHEE}.
 *
 * @param prixUnitaire   prix actuel de la variante
 * @param prixPrecedent  prix vu par le client, s'il a changé depuis (« passé de X à Y ») ; sinon null
 * @param quantiteMax    quantité commandable (0 si indisponible)
 * @param probleme       null si la ligne est commandable telle quelle
 */
public record LignePanierReponse(
        long id,
        long varianteId,
        String sku,
        String nomProduit,
        String slugProduit,
        String marque,
        String categorie,
        String libelleVariante,
        ImageReponse image,
        long prixUnitaire,
        Long prixPrecedent,
        int quantite,
        int quantiteMax,
        Disponibilite disponibilite,
        long totalLigne,
        Probleme probleme) {

    public enum Probleme {
        /** Produit retiré, masqué ou en rupture : la ligne ne compte pas dans le total. */
        INDISPONIBLE,
        /** Quantité supérieure à ce qui est commandable aujourd'hui (stock ou limite par ligne). */
        QUANTITE_TROP_ELEVEE
    }
}
