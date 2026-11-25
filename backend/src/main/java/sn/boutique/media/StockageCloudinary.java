package sn.boutique.media;

import java.util.HashMap;
import java.util.Map;

import com.cloudinary.Cloudinary;
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

    private final ProprietesMedias proprietes;
    private volatile Cloudinary client;

    public StockageCloudinary(ProprietesMedias proprietes) {
        this.proprietes = proprietes;
    }

    @Override
    public MediaStocke envoyerImage(FichierVerifie fichier, String idPublic) {
        Map<String, Object> options = optionsCommunes("image");
        options.put("public_id", idPublic);
        // Mode « dossiers dynamiques » de Cloudinary : range aussi le fichier dans la médiathèque
        options.put("asset_folder", idPublic.substring(0, idPublic.lastIndexOf('/')));
        options.put("overwrite", false);
        options.put("unique_filename", false);
        options.put("use_filename", false);
        try {
            Map<?, ?> reponse = client().uploader().upload(fichier.contenu(), options);
            return new MediaStocke((String) reponse.get("public_id"), nombre(reponse.get("version")),
                    (String) reponse.get("format"), entier(reponse.get("width")), entier(reponse.get("height")),
                    nombre(reponse.get("bytes")));
        } catch (Exception e) {
            throw new StockageIndisponibleException("Envoi de l'image impossible (" + idPublic + ") : "
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
