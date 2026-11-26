package sn.boutique.contenu.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import sn.boutique.contenu.modele.BoutonPrincipalFiche;
import sn.boutique.contenu.modele.ElementConfiance;
import sn.boutique.contenu.modele.FraisRetour;
import sn.boutique.contenu.modele.HoraireOuverture;
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
        boolean avisReservesAuxAcheteurs,
        BoutonPrincipalFiche boutonPrincipalFiche,
        String delaiLivraison,
        PolitiqueRetour politiqueRetour,
        AdresseStructuree adresseStructuree,
        BoutiquePhysique boutiquePhysique,
        Site site) {

    /**
     * Adresse publique du site (canoniques, JSON-LD) et autorisation d'indexer, venant de la configuration
     * du serveur ({@code URL_SITE}, {@code INDEXATION_AUTORISEE}) et non de la base.
     */
    public record Site(String url, boolean indexationAutorisee) {
    }

    /**
     * Politique de retour (JSON-LD MerchantReturnPolicy).
     *
     * @param delaiJours null si les retours sont refusés
     */
    public record PolitiqueRetour(boolean acceptes, Integer delaiJours, FraisRetour frais, boolean enBoutique,
                                  boolean parLivreur) {
    }

    /** Adresse postale structurée (JSON-LD PostalAddress). Null tant que rue, ville et région ne sont pas toutes saisies. */
    public record AdresseStructuree(String rue, String ville, String region, String codePostal) {
    }

    /**
     * Boutique ouverte au public (JSON-LD Store). Null si {@code boutiquePhysique} est faux.
     *
     * @param latitude  null si les coordonnées ne sont pas renseignées (avec longitude)
     */
    public record BoutiquePhysique(BigDecimal latitude, BigDecimal longitude, List<HoraireOuverture> horaires) {
    }

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

    public static ParametresPublicsReponse depuis(ParametresSite p, Site site) {
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
                p.isAvisReservesAuxAcheteurs(), p.getBoutonPrincipalFiche(),
                vide(p.getDelaiLivraison()) ? null : p.getDelaiLivraison().strip(),
                new PolitiqueRetour(p.isRetoursAcceptes(), p.isRetoursAcceptes() ? p.getDelaiRetourJours() : null,
                        p.getFraisRetour(), p.isRetourEnBoutique(), p.isRetourParLivreur()),
                adresse(p), boutique(p), site);
    }

    private static AdresseStructuree adresse(ParametresSite p) {
        if (vide(p.getBoutiqueRue()) || vide(p.getBoutiqueVille()) || vide(p.getBoutiqueRegion())) {
            return null;
        }
        return new AdresseStructuree(p.getBoutiqueRue().strip(), p.getBoutiqueVille().strip(),
                p.getBoutiqueRegion().strip(), vide(p.getBoutiqueCodePostal()) ? null : p.getBoutiqueCodePostal().strip());
    }

    private static BoutiquePhysique boutique(ParametresSite p) {
        if (!p.isBoutiquePhysique() || adresse(p) == null) {
            return null;
        }
        // Une plage mal formée (saisie à la main en base) est écartée plutôt que publiée
        return new BoutiquePhysique(p.getBoutiqueLatitude(), p.getBoutiqueLongitude(),
                p.getHorairesOuverture().stream().filter(HoraireOuverture::estValide).toList());
    }

    private static boolean vide(String texte) {
        return texte == null || texte.isBlank();
    }
}
