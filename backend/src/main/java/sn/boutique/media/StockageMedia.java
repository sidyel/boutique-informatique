package sn.boutique.media;

/**
 * Stockage des fichiers médias. Implémentation : {@link StockageCloudinary} ; les tests utilisent un faux
 * stockage en mémoire et n'appellent jamais Cloudinary.
 * <p>
 * Les appels sont lents et faillibles (réseau) : ne jamais les faire dans une transaction de base de données
 * ouverte ; en cas d'échec après un envoi, supprimer le fichier envoyé (compensation).
 */
public interface StockageMedia {

    /** Envoie une image déjà vérifiée ({@link VerificationFichiers}) sous l'idPublic donné. */
    MediaStocke envoyerImage(FichierVerifie fichier, String idPublic);

    /** Supprime un fichier ; sans effet s'il n'existe plus. */
    void supprimer(String idPublic, TypeRessource type);

    enum TypeRessource {
        IMAGE("image"), VIDEO("video");

        private final String nomCloudinary;

        TypeRessource(String nomCloudinary) {
            this.nomCloudinary = nomCloudinary;
        }

        public String nomCloudinary() {
            return nomCloudinary;
        }
    }

    /**
     * Fichier stocké.
     *
     * @param version numéro de version Cloudinary (présent dans les URL, change à chaque remplacement)
     * @param format  extension stockée (jpg, png, webp, svg, mp4…)
     */
    record MediaStocke(String idPublic, long version, String format, Integer largeur, Integer hauteur, long octets) {
    }
}
