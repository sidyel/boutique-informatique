package sn.boutique.contenu.dto;

import java.util.List;
import java.util.Map;

import sn.boutique.contenu.modele.ElementConfiance;
import sn.boutique.contenu.modele.ParametresSite;
import sn.boutique.media.TransformationsImages;

/** Paramètres du site lisibles par tous : identité, contact, identifiants publics de suivi. */
public record ParametresPublicsReponse(
        String nomSite,
        String logoUrl,
        String logoSombreUrl,
        String faviconUrl,
        Favicons favicons,
        String imagePartageUrl,
        String telephone,
        String whatsapp,
        String email,
        String adresse,
        String horaires,
        Map<String, String> liensReseauxSociaux,
        String messageBarreSuperieure,
        List<ElementConfiance> elementsConfiance,
        Suivi suivi,
        Verification verification,
        boolean avisReservesAuxAcheteurs) {

    /**
     * Favicon décliné par Cloudinary à partir d'une seule image carrée (PNG) : onglet (32), écran d'accueil iOS
     * (apple-touch-icon, 180) et manifeste web (192, 512). Null tant qu'aucun favicon n'est envoyé.
     */
    public record Favicons(String taille32, String appleTouch180, String manifeste192, String manifeste512) {

        static Favicons depuis(String url) {
            if (url == null) {
                return null;
            }
            return new Favicons(TransformationsImages.favicon(url, 32), TransformationsImages.favicon(url, 180),
                    TransformationsImages.favicon(url, 192), TransformationsImages.favicon(url, 512));
        }
    }

    /** Identifiants publics des outils de mesure (jamais de secret : voir {@link ParametresSite}). */
    public record Suivi(String idGoogleTagManager, String idGa4, String idGoogleAds, String libelleConversionAchat,
                        String idPixelMeta) {
    }

    /** Codes de vérification de propriété du site (balises meta). */
    public record Verification(String google, String meta) {
    }

    public static ParametresPublicsReponse depuis(ParametresSite p) {
        // Image de partage : recadrée en 1200 × 630 (format Open Graph)
        return new ParametresPublicsReponse(p.getNomSite(), p.getLogoUrl(), p.getLogoSombreUrl(), p.getFaviconUrl(),
                Favicons.depuis(p.getFaviconUrl()), TransformationsImages.partage(p.getImagePartageUrl()), p.getTelephone(), p.getWhatsapp(), p.getEmail(), p.getAdresse(),
                p.getHoraires(), Map.copyOf(p.getLiensReseauxSociaux()),
                vide(p.getMessageBarreSuperieure()) ? null : p.getMessageBarreSuperieure().strip(),
                // Un élément sans titre est masqué
                p.getElementsConfiance().stream().filter(ElementConfiance::estAffichable).toList(),
                new Suivi(p.getIdGoogleTagManager(), p.getIdGa4(), p.getIdGoogleAds(), p.getLibelleConversionAchat(),
                        p.getIdPixelMeta()),
                new Verification(p.getCodeVerificationGoogle(), p.getCodeVerificationMeta()),
                p.isAvisReservesAuxAcheteurs());
    }

    private static boolean vide(String texte) {
        return texte == null || texte.isBlank();
    }
}
