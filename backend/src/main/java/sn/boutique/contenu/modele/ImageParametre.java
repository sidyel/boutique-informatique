package sn.boutique.contenu.modele;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import sn.boutique.media.UsageImage;

/** Images des paramètres du site, avec leur segment d'URL d'administration et leurs règles. */
public enum ImageParametre {

    LOGO("logo", UsageImage.LOGO, ParametresSite::getLogoIdPublic, ParametresSite::setLogoUrl,
            ParametresSite::setLogoIdPublic),
    LOGO_SOMBRE("logo-sombre", UsageImage.LOGO, ParametresSite::getLogoSombreIdPublic, ParametresSite::setLogoSombreUrl,
            ParametresSite::setLogoSombreIdPublic),
    FAVICON("favicon", UsageImage.FAVICON, ParametresSite::getFaviconIdPublic, ParametresSite::setFaviconUrl,
            ParametresSite::setFaviconIdPublic),
    IMAGE_PARTAGE("image-partage", UsageImage.PARTAGE, ParametresSite::getImagePartageIdPublic,
            ParametresSite::setImagePartageUrl, ParametresSite::setImagePartageIdPublic);

    private final String chemin;
    private final UsageImage usage;
    private final Function<ParametresSite, String> idPublicActuel;
    private final BiConsumer<ParametresSite, String> definirUrl;
    private final BiConsumer<ParametresSite, String> definirIdPublic;

    ImageParametre(String chemin, UsageImage usage, Function<ParametresSite, String> idPublicActuel,
                   BiConsumer<ParametresSite, String> definirUrl, BiConsumer<ParametresSite, String> definirIdPublic) {
        this.chemin = chemin;
        this.usage = usage;
        this.idPublicActuel = idPublicActuel;
        this.definirUrl = definirUrl;
        this.definirIdPublic = definirIdPublic;
    }

    public static Optional<ImageParametre> depuisChemin(String chemin) {
        return Arrays.stream(values()).filter(image -> image.chemin.equals(chemin)).findFirst();
    }

    /** Segment d'URL, aussi nom de base de l'idPublic (« logo-sombre-k3f9q2mz »). */
    public String chemin() {
        return chemin;
    }

    public UsageImage usage() {
        return usage;
    }

    public String idPublicActuel(ParametresSite parametres) {
        return idPublicActuel.apply(parametres);
    }

    public void remplacer(ParametresSite parametres, String url, String idPublic) {
        definirUrl.accept(parametres, url);
        definirIdPublic.accept(parametres, idPublic);
    }
}
