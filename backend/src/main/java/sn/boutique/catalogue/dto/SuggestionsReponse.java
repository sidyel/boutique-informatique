package sn.boutique.catalogue.dto;

import java.util.List;

/** Autocomplétion de la barre de recherche : produits, catégories et marques. */
public record SuggestionsReponse(
        List<ProduitResumeReponse> produits,
        List<LienReponse> categories,
        List<LienReponse> marques) {
}
