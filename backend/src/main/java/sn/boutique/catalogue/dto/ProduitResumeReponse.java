package sn.boutique.catalogue.dto;

import java.math.BigDecimal;

import sn.boutique.catalogue.modele.EtatProduit;

/**
 * Carte produit (listes, recherche, produits similaires).
 * <p>
 * Le prix, le prix barré, la disponibilité et le SKU sont ceux d'une <strong>variante retenue</strong> :
 * la variante active la moins chère parmi celles qui correspondent aux filtres de la liste (RAM, prix,
 * disponibilité…), ou la moins chère tout court sans filtre. Le lien de la carte ouvre la fiche avec
 * cette variante présélectionnée ({@code ?variante=varianteId}).
 *
 * @param skuParDefaut SKU de la variante retenue : {@code item_id} des événements de suivi (= id des flux produits)
 * @param reduction    pourcentage de réduction arrondi, ou {@code null} sans prix barré
 */
public record ProduitResumeReponse(
        Long id,
        String slug,
        String nom,
        LienReponse marque,
        LienReponse categorie,
        ImageReponse image,
        Long varianteId,
        String skuParDefaut,
        Long prix,
        Long prixBarre,
        Integer reduction,
        EtatProduit etat,
        Disponibilite disponibilite,
        BigDecimal noteMoyenne,
        int nombreAvis,
        int nombreVariantes) {
}
