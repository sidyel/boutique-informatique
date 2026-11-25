package sn.boutique.contenu.dto;

import java.util.Map;

import sn.boutique.contenu.modele.ParametresSite;

/** Paramètres du site lisibles par tous : identité, contact, identifiants publics de suivi. */
public record ParametresPublicsReponse(
        String nomSite,
        String logoUrl,
        String logoSombreUrl,
        String faviconUrl,
        String imagePartageUrl,
        String telephone,
        String whatsapp,
        String email,
        String adresse,
        String horaires,
        Map<String, String> liensReseauxSociaux,
        Suivi suivi,
        Verification verification,
        boolean avisReservesAuxAcheteurs) {

    /** Identifiants publics des outils de mesure (jamais de secret : voir {@link ParametresSite}). */
    public record Suivi(String idGoogleTagManager, String idGa4, String idGoogleAds, String libelleConversionAchat,
                        String idPixelMeta) {
    }

    /** Codes de vérification de propriété du site (balises meta). */
    public record Verification(String google, String meta) {
    }

    public static ParametresPublicsReponse depuis(ParametresSite p) {
        return new ParametresPublicsReponse(p.getNomSite(), p.getLogoUrl(), p.getLogoSombreUrl(), p.getFaviconUrl(),
                p.getImagePartageUrl(), p.getTelephone(), p.getWhatsapp(), p.getEmail(), p.getAdresse(),
                p.getHoraires(), Map.copyOf(p.getLiensReseauxSociaux()),
                new Suivi(p.getIdGoogleTagManager(), p.getIdGa4(), p.getIdGoogleAds(), p.getLibelleConversionAchat(),
                        p.getIdPixelMeta()),
                new Verification(p.getCodeVerificationGoogle(), p.getCodeVerificationMeta()),
                p.isAvisReservesAuxAcheteurs());
    }
}
