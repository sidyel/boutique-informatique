package sn.boutique.catalogue.dto;

import java.util.List;

import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeMedia;
import sn.boutique.catalogue.validation.TexteAlternatifValidateur;

/**
 * Médias d'un produit dans le back-office, avec ce qu'il faut pour le formulaire d'envoi : limites et textes
 * alternatifs suggérés (produit seul, puis par variante).
 */
public record MediasProduitAdminReponse(Long produitId, String slug, String nom, boolean actif, int nombreImages,
                                        int maxImages, String texteAlternatifSuggere,
                                        List<SuggestionVariante> suggestionsParVariante,
                                        List<MediaAdminReponse> medias) {

    public record SuggestionVariante(Long varianteId, String libelle, String texteAlternatifSuggere) {
    }

    public static MediasProduitAdminReponse depuis(Produit produit, int maxImages) {
        List<MediaAdminReponse> medias = produit.getMedias().stream().map(MediaAdminReponse::depuis).toList();
        int images = (int) produit.getMedias().stream().filter(media -> media.getType() == TypeMedia.IMAGE).count();
        List<SuggestionVariante> suggestions = produit.getVariantes().stream()
                .map(variante -> new SuggestionVariante(variante.getId(), variante.getLibelle(),
                        TexteAlternatifValidateur.suggestion(produit.getNom(), variante.getLibelle())))
                .toList();
        return new MediasProduitAdminReponse(produit.getId(), produit.getSlug(), produit.getNom(), produit.isActif(),
                images, maxImages, TexteAlternatifValidateur.suggestion(produit.getNom(), null), suggestions, medias);
    }
}
