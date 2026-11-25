package sn.boutique.media;

/**
 * Fichier contrôlé, prêt à être stocké : type réel détecté sur le contenu (jamais sur le nom ou l'en-tête
 * Content-Type envoyés), dimensions lues dans les en-têtes, SVG nettoyé.
 *
 * @param largeur  {@code null} pour un SVG (vectoriel)
 * @param hauteur  {@code null} pour un SVG
 */
public record FichierVerifie(byte[] contenu, String typeMime, String extension, Integer largeur, Integer hauteur) {

    public boolean estSvg() {
        return "image/svg+xml".equals(typeMime);
    }
}
