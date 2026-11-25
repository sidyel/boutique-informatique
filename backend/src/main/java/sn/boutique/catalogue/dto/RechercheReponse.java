package sn.boutique.catalogue.dto;

import java.util.List;

/**
 * Résultats d'une recherche.
 *
 * @param requete     texte recherché, nettoyé
 * @param approximatif {@code true} si aucun résultat exact : les résultats tolèrent les fautes de frappe
 *                    (« Résultats approchants pour… »)
 * @param categories  catégories des résultats, avec leur nombre (sans le filtre de catégorie)
 * @param marques     marques des résultats, avec leur nombre (sans le filtre de marque)
 * @param suggestions propositions quand rien n'est trouvé, même approximativement ; {@code null} sinon
 */
public record RechercheReponse(
        String requete,
        boolean approximatif,
        List<ProduitResumeReponse> elements,
        int page,
        int taille,
        long totalElements,
        int totalPages,
        List<FiltresReponse.Option> categories,
        List<FiltresReponse.Option> marques,
        Suggestions suggestions) {

    /** Pour la page « aucun résultat » : catégories principales et produits mis en avant. */
    public record Suggestions(List<LienReponse> categories, List<ProduitResumeReponse> produits) {
    }
}
