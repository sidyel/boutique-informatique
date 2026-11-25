package sn.boutique.catalogue.dto;

/** Page marque ({@code /marque/asus}) : les produits passent par {@code /api/produits?marque=}. */
public record MarqueReponse(
        String nom,
        String slug,
        String logoUrl,
        String description,
        String metaTitre,
        String metaDescription,
        long nombreProduits) {
}
