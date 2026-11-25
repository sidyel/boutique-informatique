package sn.boutique.catalogue.dto;

import java.util.List;

import sn.boutique.catalogue.modele.TypeCaracteristique;

/**
 * Filtres disponibles d'une catégorie, avec le nombre de produits pour chaque valeur (facettes).
 * <p>
 * Le nombre affiché pour une valeur tient compte de <strong>tous les autres filtres</strong> actifs, mais pas
 * de ceux de sa propre famille : cocher « Asus » laisse voir combien de produits « Lenovo » correspondent
 * au reste de la sélection. Comme pour la liste, un produit compte pour une valeur si au moins une de
 * ses variantes actives a cette valeur <em>et</em> satisfait les filtres de variante (prix, disponibilité…).
 *
 * @param totalProduits  produits correspondant à tous les filtres
 * @param prix           prix extrêmes des variantes qui conviennent (sans le filtre de prix), ou {@code null}
 * @param sousCategories sous-catégories directes (navigation), avec leur nombre de produits
 */
public record FiltresReponse(
        long totalProduits,
        Prix prix,
        List<Option> sousCategories,
        List<Option> marques,
        List<Option> etats,
        Disponible disponible,
        List<Caracteristique> caracteristiques) {

    public record Prix(Long min, Long max) {
    }

    /** @param valeur valeur du paramètre de filtre (slug, code, valeur brute) ; {@code libelle} : texte affiché */
    public record Option(String valeur, String libelle, long nombre, boolean selectionne) {
    }

    public record Disponible(long enStock, boolean selectionne) {
    }

    public record Caracteristique(
            String cle,
            String libelle,
            String unite,
            TypeCaracteristique type,
            String groupe,
            List<Option> valeurs) {
    }
}
