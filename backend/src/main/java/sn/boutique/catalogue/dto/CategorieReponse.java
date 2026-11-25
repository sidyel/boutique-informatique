package sn.boutique.catalogue.dto;

import java.util.List;

/**
 * En-tête et textes SEO d'une page catégorie.
 *
 * @param nombreProduits produits actifs de la catégorie et de ses sous-catégories
 * @param filAriane      ancêtres, de la racine au parent direct (la catégorie elle-même n'y figure pas)
 * @param enfants        sous-catégories directes, avec leur nombre de produits
 */
public record CategorieReponse(
        String nom,
        String slug,
        String description,
        String imageUrl,
        String banniereUrl,
        String texteSeo,
        String metaTitre,
        String metaDescription,
        long nombreProduits,
        List<LienReponse> filAriane,
        List<CategorieNoeudReponse> enfants) {
}
