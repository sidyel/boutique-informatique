package sn.boutique.media;

/** Usage d'une image envoyée : fixe la taille minimale et le dossier Cloudinary. */
public enum UsageImage {

    /** Photo de produit : zoom de la fiche, flux Google Merchant et Meta. */
    PRODUIT("produits", false),
    /** Bannière d'accueil ou d'en-tête de catégorie. */
    BANNIERE("bannieres", false),
    /** Image de partage par défaut (Open Graph). */
    PARTAGE("site", false),
    /** Logo, logo sombre et favicon : seul usage où le SVG est accepté (nettoyé). */
    LOGO("site", true);

    private final String dossier;
    private final boolean svgAccepte;

    UsageImage(String dossier, boolean svgAccepte) {
        this.dossier = dossier;
        this.svgAccepte = svgAccepte;
    }

    public String dossier() {
        return dossier;
    }

    public boolean svgAccepte() {
        return svgAccepte;
    }
}
