package sn.boutique.media;

import java.io.IOException;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.image.JpegParser;
import org.apache.tika.parser.image.WebPParser;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import sn.boutique.noyau.erreur.RegleMetierException;

/**
 * Contrôle des images envoyées, avant tout stockage.
 * <ol>
 *   <li>poids maximal ;</li>
 *   <li>type réel détecté par Tika sur les premiers octets (signature), sans tenir compte du nom du fichier ni
 *       du Content-Type annoncé : JPEG, PNG ou WebP, et SVG pour le logo seulement ;</li>
 *   <li>dimensions lues dans les en-têtes par les analyseurs de Tika ({@code SOF} JPEG, {@code IHDR} PNG,
 *       {@code VP8}/{@code VP8X} WebP) : l'image n'est jamais décodée dans le backend. Une image de plus de
 *       8 000 px de côté est refusée (« bombe de décompression » : quelques Ko qui occupent des gigaoctets une
 *       fois décodés par Cloudinary ou un navigateur), une photo produit de moins de 800 px aussi.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationFichiers {

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";
    public static final String SVG = "image/svg+xml";

    /** Analyseur choisi explicitement par type : aucun autre format n'est jamais analysé. */
    private static final Map<String, Parser> ANALYSEURS = Map.of(
            JPEG, new JpegParser(),
            PNG, new ImageParser(),
            WEBP, new WebPParser());
    private static final Map<String, String> EXTENSIONS = Map.of(JPEG, "jpg", PNG, "png", WEBP, "webp", SVG, "svg");

    private final Tika tika = new Tika();
    private final ProprietesMedias proprietes;
    private final NettoyeurSvg nettoyeurSvg;

    public FichierVerifie verifierImage(byte[] contenu, UsageImage usage) {
        if (contenu == null || contenu.length == 0) {
            throw refus("FICHIER_VIDE", "Le fichier est vide.");
        }
        String type = tika.detect(contenu);
        if (SVG.equals(type)) {
            return verifierSvg(contenu, usage);
        }
        long tailleMax = proprietes.images().tailleMax().toBytes();
        if (contenu.length > tailleMax) {
            throw refus("FICHIER_TROP_LOURD", "L'image pèse %s Mo, le maximum est %s Mo."
                    .formatted(enMo(contenu.length), enMo(tailleMax)));
        }
        Parser analyseur = ANALYSEURS.get(type);
        if (analyseur == null) {
            throw refus("TYPE_NON_AUTORISE", "Format non accepté (%s) : JPEG, PNG ou WebP%s."
                    .formatted(type, usage.svgAccepte() ? ", ou SVG" : ""));
        }
        int[] dimensions = dimensions(analyseur, contenu, type);
        int largeur = dimensions[0];
        int hauteur = dimensions[1];
        int coteMax = proprietes.images().coteMax();
        if (Math.max(largeur, hauteur) > coteMax) {
            throw refus("IMAGE_TROP_GRANDE", "L'image mesure %d × %d px : %d px de côté au maximum."
                    .formatted(largeur, hauteur, coteMax));
        }
        int coteMin = proprietes.images().coteMinPour(usage);
        if (Math.min(largeur, hauteur) < coteMin) {
            throw refus("IMAGE_TROP_PETITE", "L'image mesure %d × %d px : au moins %d px sur le plus petit côté%s."
                    .formatted(largeur, hauteur, coteMin,
                            usage == UsageImage.PRODUIT ? " pour une photo produit (zoom net, Google Merchant)" : ""));
        }
        return new FichierVerifie(contenu, type, EXTENSIONS.get(type), largeur, hauteur);
    }

    private FichierVerifie verifierSvg(byte[] contenu, UsageImage usage) {
        if (!usage.svgAccepte()) {
            throw refus("TYPE_NON_AUTORISE", "Le SVG n'est accepté que pour le logo et le favicon.");
        }
        long tailleMax = proprietes.svg().tailleMax().toBytes();
        if (contenu.length > tailleMax) {
            throw refus("FICHIER_TROP_LOURD", "Le SVG pèse %d Ko, le maximum est %d Ko."
                    .formatted(contenu.length / 1024, tailleMax / 1024));
        }
        NettoyeurSvg.SvgNettoye nettoye = nettoyeurSvg.nettoyer(contenu);
        if (!nettoye.retraits().isEmpty()) {
            log.warn("SVG nettoyé, éléments retirés : {}", nettoye.retraits());
        }
        return new FichierVerifie(nettoye.contenu(), SVG, "svg", null, null);
    }

    /** Largeur et hauteur lues dans les métadonnées, sans décoder les pixels. */
    private static int[] dimensions(Parser analyseur, byte[] contenu, String type) {
        Metadata metadonnees = new Metadata();
        // L'analyseur PNG (ImageIO) choisit son lecteur d'après ce type : sans lui, aucune dimension
        metadonnees.set(HttpHeaders.CONTENT_TYPE, type);
        try (TikaInputStream flux = TikaInputStream.get(contenu)) {
            analyseur.parse(flux, new DefaultHandler(), metadonnees, new ParseContext());
        } catch (IOException | SAXException | TikaException | RuntimeException e) {
            throw refus("IMAGE_ILLISIBLE", "Image illisible ou endommagée (%s).".formatted(type));
        }
        // JPEG et PNG : clés TIFF ; WebP : clés propres à l'analyseur (metadata-extractor)
        Integer largeur = entier(premiere(metadonnees, TIFF.IMAGE_WIDTH.getName(), "img:Image Width"));
        Integer hauteur = entier(premiere(metadonnees, TIFF.IMAGE_LENGTH.getName(), "img:Image Height"));
        if (largeur == null || hauteur == null || largeur <= 0 || hauteur <= 0) {
            throw refus("IMAGE_ILLISIBLE", "Dimensions de l'image introuvables (%s).".formatted(type));
        }
        return new int[] {largeur, hauteur};
    }

    private static String premiere(Metadata metadonnees, String... cles) {
        for (String cle : cles) {
            if (metadonnees.get(cle) != null) {
                return metadonnees.get(cle);
            }
        }
        return null;
    }

    /** « 4000 », voire « 4000 pixels » selon l'analyseur. */
    private static Integer entier(String valeur) {
        if (valeur == null) {
            return null;
        }
        String chiffres = valeur.strip().split("\\s+")[0];
        try {
            return Integer.valueOf(chiffres);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String enMo(long octets) {
        return "%.1f".formatted(octets / 1_048_576.0).replace('.', ',');
    }

    private static RegleMetierException refus(String code, String message) {
        return new RegleMetierException(code, message);
    }
}
