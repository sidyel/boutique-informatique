package sn.boutique.media;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.exceptions.NotFound;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stockage sur Cloudinary. La clé ({@code CLOUDINARY_URL}) vient de l'environnement, n'est jamais journalisée
 * et n'apparaît dans aucun message d'erreur.
 * <p>
 * Sans clé, l'application démarre quand même (développement, CI) : seuls les envois échouent, avec un
 * message explicite.
 */
@Slf4j
@Component
public class StockageCloudinary implements StockageMedia {

    /** Réencodage au format le mieux adapté au navigateur, qualité automatique, métadonnées retirées. */
    static final String TRANSFORMATION_DIFFUSION = "f_auto,q_auto";
    /**
     * SVG : jamais f_auto, qui le convertirait en image matricielle à sa taille d'origine (logo flou, constaté
     * lors de l'essai réel). Servi en vectoriel, nettoyé une seconde fois par Cloudinary à la diffusion ; un SVG
     * n'a pas de métadonnées EXIF à retirer (ADR 0005 §4).
     */
    static final String TRANSFORMATION_SVG = "fl_sanitize";
    /** Aperçu d'une vidéo : image choisie automatiquement par Cloudinary, puis même transformation. */
    static final String TRANSFORMATION_MINIATURE = "so_auto," + TRANSFORMATION_DIFFUSION;

    private final ProprietesMedias proprietes;
    private volatile Cloudinary client;

    public StockageCloudinary(ProprietesMedias proprietes) {
        this.proprietes = proprietes;
    }

    @Override
    public MediaStocke envoyerImage(FichierVerifie fichier, String idPublic) {
        return envoyer(fichier.contenu(), idPublic, "de l'image");
    }

    @Override
    public MediaStocke envoyerImageDistante(String url, String idPublic) {
        return envoyer(url, idPublic, "de la miniature");
    }

    private MediaStocke envoyer(Object source, String idPublic, String quoi) {
        Map<String, Object> options = optionsCommunes("image");
        options.put("public_id", idPublic);
        // Mode « dossiers dynamiques » de Cloudinary : range aussi le fichier dans la médiathèque
        options.put("asset_folder", dossierDe(idPublic));
        options.put("overwrite", false);
        options.put("unique_filename", false);
        options.put("use_filename", false);
        try {
            return mediaStocke(client().uploader().upload(source, options));
        } catch (Exception e) {
            throw new StockageIndisponibleException("Envoi " + quoi + " impossible (" + idPublic + ") : "
                    + e.getMessage(), e);
        }
    }

    @Override
    public String urlImage(MediaStocke media) {
        return urlImage(client().config.cloudName, media);
    }

    static String urlImage(String nomDuCloud, MediaStocke media) {
        String transformation = "svg".equals(media.format()) ? TRANSFORMATION_SVG : TRANSFORMATION_DIFFUSION;
        return "https://res.cloudinary.com/" + nomDuCloud + "/image/upload/" + transformation + "/v"
                + media.version() + "/" + media.idPublic() + "." + media.format();
    }

    @Override
    public String urlVideo(MediaStocke video) {
        return "https://res.cloudinary.com/" + client().config.cloudName + "/video/upload/" + TRANSFORMATION_DIFFUSION
                + "/v" + video.version() + "/" + video.idPublic() + "." + video.format();
    }

    @Override
    public String urlMiniatureVideo(MediaStocke video) {
        return "https://res.cloudinary.com/" + client().config.cloudName + "/video/upload/" + TRANSFORMATION_MINIATURE
                + "/v" + video.version() + "/" + video.idPublic() + ".jpg";
    }

    /**
     * Seuls ces paramètres sont signés : le navigateur ne peut ni changer l'idPublic, ni écraser un fichier,
     * ni envoyer un autre format. La ressource vidéo est fixée par l'URL d'envoi et revérifiée à la
     * déclaration.
     */
    @Override
    public SignatureEnvoi signerEnvoiVideo(String idPublic, Instant horodatage) {
        Cloudinary cloudinary = client();
        Map<String, Object> signes = new LinkedHashMap<>();
        signes.put("timestamp", String.valueOf(horodatage.getEpochSecond()));
        signes.put("public_id", idPublic);
        signes.put("asset_folder", dossierDe(idPublic));
        signes.put("overwrite", "false");
        signes.put("allowed_formats", String.join(",", proprietes.videos().formats()));
        String signature = cloudinary.apiSignRequest(signes, cloudinary.config.apiSecret,
                cloudinary.config.signatureVersion);

        Map<String, String> parametres = new LinkedHashMap<>();
        signes.forEach((cle, valeur) -> parametres.put(cle, String.valueOf(valeur)));
        parametres.put("api_key", cloudinary.config.apiKey);
        parametres.put("signature", signature);
        return new SignatureEnvoi("https://api.cloudinary.com/v1_1/" + cloudinary.config.cloudName + "/video/upload",
                parametres);
    }

    @Override
    public Optional<MediaStocke> lireVideo(String idPublic) {
        try {
            return Optional.of(mediaStocke(client().api().resource(idPublic, optionsCommunes("video"))));
        } catch (NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new StockageIndisponibleException("Lecture de la vidéo impossible (" + idPublic + ") : "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void supprimer(String idPublic, TypeRessource type) {
        Map<String, Object> options = optionsCommunes(type.nomCloudinary());
        // Vide aussi le cache du CDN : l'ancienne URL cesse de servir le fichier
        options.put("invalidate", true);
        try {
            Map<?, ?> reponse = client().uploader().destroy(idPublic, options);
            Object resultat = reponse.get("result");
            if (!"ok".equals(resultat) && !"not found".equals(resultat)) {
                throw new StockageIndisponibleException("Suppression refusée par Cloudinary (" + idPublic + ") : "
                        + resultat, null);
            }
        } catch (StockageIndisponibleException e) {
            throw e;
        } catch (Exception e) {
            throw new StockageIndisponibleException("Suppression impossible (" + idPublic + ") : " + e.getMessage(), e);
        }
    }

    private static String dossierDe(String idPublic) {
        return idPublic.substring(0, idPublic.lastIndexOf('/'));
    }

    private static MediaStocke mediaStocke(Map<?, ?> reponse) {
        return new MediaStocke((String) reponse.get("public_id"), nombre(reponse.get("version")),
                (String) reponse.get("format"), entier(reponse.get("width")), entier(reponse.get("height")),
                nombre(reponse.get("bytes")));
    }

    private Map<String, Object> optionsCommunes(String typeRessource) {
        Map<String, Object> options = new HashMap<>();
        options.put("resource_type", typeRessource);
        options.put("timeout", (int) proprietes.delai().toSeconds());
        return options;
    }

    private Cloudinary client() {
        Cloudinary existant = client;
        if (existant != null) {
            return existant;
        }
        synchronized (this) {
            if (client == null) {
                client = creerClient(proprietes.cloudinaryUrl());
            }
            return client;
        }
    }

    /** Le contenu de l'URL n'est jamais recopié dans un message : elle contient le secret de l'API. */
    static Cloudinary creerClient(String url) {
        if (url == null || url.isBlank()) {
            throw new StockageIndisponibleException(
                    "Stockage des médias non configuré : définir CLOUDINARY_URL dans l'environnement.", null);
        }
        if (!url.matches("cloudinary://[^:<>\\s]+:[^@<>\\s]+@[A-Za-z0-9_-]+")) {
            throw new StockageIndisponibleException(
                    "CLOUDINARY_URL invalide : format attendu cloudinary://<cle>:<secret>@<nom-du-cloud>.", null);
        }
        Cloudinary cloudinary = new Cloudinary(url);
        cloudinary.config.secure = true;
        return cloudinary;
    }

    private static long nombre(Object valeur) {
        return valeur instanceof Number n ? n.longValue() : 0L;
    }

    private static Integer entier(Object valeur) {
        return valeur instanceof Number n ? n.intValue() : null;
    }
}
