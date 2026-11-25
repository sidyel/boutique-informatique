package sn.boutique.catalogue.dto;

import java.util.List;

/**
 * Sections de produits de l'accueil, dans l'ordre d'affichage. Une section sans produit est absente.
 */
public record AccueilReponse(List<Section> sections) {

    /**
     * @param code  {@code nouveautes}, {@code meilleures-ventes}, {@code selection} ou {@code promotions}
     * @param titre titre affiché, choisi par le serveur selon les données (jamais « Meilleures ventes » sans ventes)
     */
    public record Section(String code, String titre, List<ProduitResumeReponse> produits) {
    }
}
