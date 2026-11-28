package sn.boutique.authentification.securite;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * QR code en SVG (aucune image bitmap, aucun appel externe). Le résultat est une adresse {@code data:image/svg+xml}
 * qu'une balise {@code <img>} affiche : un SVG chargé ainsi n'exécute aucun script.
 */
public final class CodeQr {

    private static final int MARGE_MODULES = 2;

    private CodeQr() {
    }

    public static String versSvg(String contenu) {
        BitMatrix matrice;
        try {
            matrice = new QRCodeWriter().encode(contenu, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, MARGE_MODULES));
        } catch (WriterException e) {
            throw new IllegalStateException("QR code impossible à générer", e);
        }
        int largeur = matrice.getWidth();
        int hauteur = matrice.getHeight();
        StringBuilder chemin = new StringBuilder();
        for (int y = 0; y < hauteur; y++) {
            int x = 0;
            while (x < largeur) {
                if (!matrice.get(x, y)) {
                    x++;
                    continue;
                }
                int debut = x;
                while (x < largeur && matrice.get(x, y)) {
                    x++;
                }
                chemin.append('M').append(debut).append(' ').append(y).append('h').append(x - debut).append("v1h-")
                        .append(x - debut).append('z');
            }
        }
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + largeur + ' ' + hauteur
                + "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path d=\""
                + chemin + "\" fill=\"#000\"/></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }
}
