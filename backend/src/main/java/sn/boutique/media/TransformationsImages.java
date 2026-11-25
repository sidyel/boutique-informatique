package sn.boutique.media;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Déclinaisons d'une URL d'image enregistrée ({@code …/image/upload/f_auto,q_auto/v<version>/<idPublic>.<ext>})
 * : la transformation de diffusion est <strong>remplacée</strong> par une autre (jamais empilée), la version et
 * l'idPublic restent intacts. Une URL qui n'est pas de cette forme (image de démonstration servie par le site)
 * est renvoyée telle quelle.
 */
public final class TransformationsImages {

    private static final Pattern URL_ENREGISTREE = Pattern.compile(
            "^(https://[^/]+/[^/]+/image/upload/)" + Pattern.quote(StockageCloudinary.TRANSFORMATION_DIFFUSION)
                    + "/(v\\d+/.+)\\.([a-z0-9]+)$");

    /** Favicon : PNG (navigateurs, apple-touch-icon et manifeste web n'acceptent pas tous WebP ou AVIF). */
    public static final int[] TAILLES_FAVICON = {32, 180, 192, 512};

    private TransformationsImages() {
    }

    /** Favicon carré de {@code taille} px, en PNG. */
    public static String favicon(String url, int taille) {
        return decliner(url, "c_fill,w_%d,h_%d,f_png,q_auto".formatted(taille, taille), "png");
    }

    /** Image Open Graph recadrée en 1200 × 630 (centre d'intérêt choisi par Cloudinary), en JPEG pour les robots. */
    public static String partage(String url) {
        return decliner(url, "c_fill,g_auto,w_1200,h_630,f_jpg,q_auto", "jpg");
    }

    static String decliner(String url, String transformation, String extension) {
        if (url == null) {
            return null;
        }
        Matcher correspondance = URL_ENREGISTREE.matcher(url);
        if (!correspondance.matches()) {
            return url;
        }
        return correspondance.group(1) + transformation + "/" + correspondance.group(2) + "." + extension;
    }
}
