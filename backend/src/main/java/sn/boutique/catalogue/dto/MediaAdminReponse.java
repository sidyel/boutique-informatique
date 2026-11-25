package sn.boutique.catalogue.dto;

import sn.boutique.catalogue.modele.MediaProduit;
import sn.boutique.catalogue.modele.TypeMedia;
import sn.boutique.catalogue.validation.TexteAlternatifValidateur;

/**
 * Média d'un produit dans le back-office.
 *
 * @param texteAlternatifSuggere « nom du produit – libellé de la variante », pour préremplir le formulaire
 * @param stocke                 vrai si le fichier est sur Cloudinary (faux pour les images de démonstration
 *                               servies par le site)
 */
public record MediaAdminReponse(Long id, TypeMedia type, String url, String urlMiniature, String texteAlternatif,
                                String texteAlternatifSuggere, int position, boolean principal, Long varianteId,
                                String libelleVariante, boolean stocke) {

    public static MediaAdminReponse depuis(MediaProduit media) {
        String libelle = media.getVariante() == null ? null : media.getVariante().getLibelle();
        return new MediaAdminReponse(media.getId(), media.getType(), media.getUrl(), media.getUrlMiniature(),
                media.getTexteAlternatif(),
                TexteAlternatifValidateur.suggestion(media.getProduit().getNom(), libelle),
                media.getPosition(), media.isPrincipal(),
                media.getVariante() == null ? null : media.getVariante().getId(), libelle, media.getIdPublic() != null);
    }
}
