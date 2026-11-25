package sn.boutique.media;

/** Usage d'une image envoyée : fixe la taille minimale et le dossier Cloudinary. */
public enum UsageImage {

    /** Photo de produit : zoom de la fiche, flux Google Merchant et Meta. */
    PRODUIT("produits", false),
    /** Bannière d'accueil ou d'en-tête de catégorie. */
    BANNIERE("bannieres", false),
    /** Image de partage par défaut (Open Graph) : au moins 600 × 315 px, format conseillé 1200 × 630. */
    PARTAGE("site", false),
    /** Favicon : image carrée d'au moins 512 px, déclinée en 32, 180, 192 et 512 px par Cloudinary. */
    FAVICON("site", false),
    /** Logo et logo sombre : seul usage où le SVG est accepté (nettoyé). */
    LOGO("site", true);

    /** Open Graph : en dessous, Facebook et WhatsApp affichent une petite vignette au lieu de la grande image. */
    public static final int PARTAGE_LARGEUR_MIN = 600;
    public static final int PARTAGE_HAUTEUR_MIN = 315;

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
