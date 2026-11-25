package sn.boutique.catalogue.dto;

import java.util.Comparator;
import java.util.List;

import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.VarianteProduit;

/**
 * Produit vu par le back-office après une modification. {@code prixMinimum} et {@code stockTotal} sont
 * calculés par la base (trigger, ADR 0004) : le produit doit avoir été relu avant de construire cette réponse.
 */
public record ProduitAdminResumeReponse(
        Long id,
        String slug,
        String nom,
        boolean actif,
        Long prixMinimum,
        Integer stockTotal,
        List<Variante> variantes) {

    public record Variante(Long id, String sku, String libelle, long prix, Long prixBarre, int stock, boolean actif) {
    }

    public static ProduitAdminResumeReponse depuis(Produit produit) {
        return new ProduitAdminResumeReponse(produit.getId(), produit.getSlug(), produit.getNom(), produit.isActif(),
                produit.getPrixMinimum(), produit.getStockTotal(),
                produit.getVariantes().stream()
                        .sorted(Comparator.comparingLong(VarianteProduit::getPrix).thenComparing(VarianteProduit::getId))
                        .map(v -> new Variante(v.getId(), v.getSku(), v.getLibelle(), v.getPrix(), v.getPrixBarre(),
                                v.getStock(), v.isActif()))
                        .toList());
    }
}
