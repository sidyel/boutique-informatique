package sn.boutique.media;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

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

    /**
     * Fait copier par le stockage une image publique (miniature YouTube) sous l'idPublic donné. L'URL doit
     * venir du code, jamais d'une saisie : le stockage ira la chercher.
     */
    MediaStocke envoyerImageDistante(String url, String idPublic);

    /**
     * URL de diffusion d'une image : toujours <strong>transformée</strong> ({@code f_auto,q_auto}), jamais
     * l'original, qui peut contenir des métadonnées EXIF (position GPS…) ; ADR 0005 §4. La version figure dans
     * l'URL : un remplacement change l'adresse, le cache du CDN et des navigateurs n'a pas à être vidé.
     */
    String urlImage(MediaStocke media);

    /** URL de diffusion d'une vidéo envoyée (transformée : format et qualité automatiques). */
    String urlVideo(MediaStocke video);

    /** Image d'aperçu d'une vidéo envoyée, générée par le stockage, en URL transformée comme les images. */
    String urlMiniatureVideo(MediaStocke video);

    /**
     * Paramètres signés d'un envoi direct navigateur → stockage, pour <strong>cet</strong> idPublic seulement,
     * en ressource vidéo, sans écrasement possible.
     *
     * @param horodatage horodatage signé ; le stockage refuse la signature une heure après
     */
    SignatureEnvoi signerEnvoiVideo(String idPublic, Instant horodatage);

    /** Vidéo réellement présente sur le stockage (vide si l'envoi n'a pas eu lieu ou n'est pas une vidéo). */
    Optional<MediaStocke> lireVideo(String idPublic);

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

    /**
     * Envoi direct : le navigateur poste le fichier et {@code parametres} à {@code urlEnvoi}. Aucun secret
     * dans les paramètres (la clé d'API est publique, le secret ne sert qu'au calcul de la signature).
     */
    record SignatureEnvoi(String urlEnvoi, Map<String, String> parametres) {
    }
}
