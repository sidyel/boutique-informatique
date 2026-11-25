package sn.boutique.catalogue.dto;

import java.util.List;

/**
 * Nœud de l'arbre des catégories (méga-menu, tuiles de l'accueil).
 *
 * @param nombreProduits produits actifs de la catégorie et de toutes ses sous-catégories
 */
public record CategorieNoeudReponse(
        String nom,
        String slug,
        String imageUrl,
        long nombreProduits,
        List<CategorieNoeudReponse> enfants) {
}
