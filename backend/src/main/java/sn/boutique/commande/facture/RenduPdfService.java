package sn.boutique.commande.facture;

import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.Locale;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Rend une facture ou un avoir en PDF à partir de {@link PieceImprimable} et du gabarit {@code pdf/piece.html}.
 * <p>
 * <strong>L'émission ne doit jamais échouer à cause d'un caractère</strong> (elle se fait dans la transaction du
 * paiement). Trois niveaux :
 * <ol>
 *   <li>police <strong>DejaVu Sans incorporée en sous-ensemble</strong> (seuls les glyphes utilisés entrent dans le PDF :
 *       quelques dizaines de Ko) : tout le latin étendu (é, ç, ñ, ŋ, Ɓ, Ɗ…), le grec et le cyrillique ;</li>
 *   <li>les écritures qui exigent un moteur de mise en forme (arabe, hébreu, écritures indiennes, thaï…) et les
 *       caractères absents de la police (émojis) sont remplacés par « ? » : écrits sans mise en forme, ils seraient
 *       faux, ce qui est pire qu'un « ? ». Le nom exact reste dans la commande et dans la facture en base ;</li>
 *   <li>si l'incorporation de la police échoue quand même, nouveau rendu avec les polices standard du PDF
 *       (Windows-1252, tout le français) et « ? » pour le reste.</li>
 * </ol>
 * Les montants utilisent l'espace insécable U+00A0.
 */
@Service
public class RenduPdfService {

    private static final Logger journal = LoggerFactory.getLogger(RenduPdfService.class);
    private static final Charset JEU_PDF = Charset.forName("windows-1252");
    private static final String POLICE_NORMALE = "polices/pdf/DejaVuSans.ttf";
    private static final String POLICE_GRASSE = "polices/pdf/DejaVuSans-Bold.ttf";
    /** Écritures dont les lettres changent de forme ou de sens : sans moteur de mise en forme, on ne les écrit pas. */
    private static final Set<Character.UnicodeScript> ECRITURES_COMPLEXES = Set.of(
            Character.UnicodeScript.ARABIC, Character.UnicodeScript.HEBREW, Character.UnicodeScript.SYRIAC,
            Character.UnicodeScript.THAANA, Character.UnicodeScript.DEVANAGARI, Character.UnicodeScript.BENGALI,
            Character.UnicodeScript.GURMUKHI, Character.UnicodeScript.GUJARATI, Character.UnicodeScript.ORIYA,
            Character.UnicodeScript.TAMIL, Character.UnicodeScript.TELUGU, Character.UnicodeScript.KANNADA,
            Character.UnicodeScript.MALAYALAM, Character.UnicodeScript.SINHALA, Character.UnicodeScript.THAI,
            Character.UnicodeScript.LAO, Character.UnicodeScript.TIBETAN, Character.UnicodeScript.MYANMAR,
            Character.UnicodeScript.KHMER, Character.UnicodeScript.NKO);
    private static final Font POLICE_DE_COUVERTURE = chargerPolice();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)
            .withZone(ZoneOffset.UTC);
    private static final char ESPACE_INSECABLE = ' ';

    private final ITemplateEngine moteurModeles;

    public RenduPdfService(ITemplateEngine moteurModeles) {
        this.moteurModeles = moteurModeles;
    }

    public byte[] rendre(PieceImprimable piece) {
        if (POLICE_DE_COUVERTURE != null) {
            try {
                return produire(piece, true);
            } catch (Exception | LinkageError e) {
                journal.warn("Rendu avec police incorporée impossible pour {} : repli sur les polices standard",
                        piece.numero(), e);
            }
        }
        try {
            return produire(piece, false);
        } catch (Exception e) {
            throw new IllegalStateException("Génération du PDF impossible pour " + piece.numero(), e);
        }
    }

    private byte[] produire(PieceImprimable piece, boolean policeIncorporee) throws Exception {
        Context contexte = new Context(Locale.FRANCE);
        contexte.setVariable("p", nettoyer(piece, policeIncorporee));
        contexte.setVariable("date", DATE.format(piece.dateEmission()));
        String html = moteurModeles.process("pdf/piece", contexte);
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream(32 * 1024)) {
            PdfRendererBuilder constructeur = new PdfRendererBuilder();
            if (policeIncorporee) {
                constructeur.useFont(() -> ressource(POLICE_NORMALE), "DejaVu Sans", 400,
                        PdfRendererBuilder.FontStyle.NORMAL, true);
                constructeur.useFont(() -> ressource(POLICE_GRASSE), "DejaVu Sans", 700,
                        PdfRendererBuilder.FontStyle.NORMAL, true);
            }
            constructeur.withHtmlContent(html, null);
            constructeur.toStream(sortie);
            constructeur.run();
            return sortie.toByteArray();
        }
    }

    private static InputStream ressource(String chemin) {
        return RenduPdfService.class.getClassLoader().getResourceAsStream(chemin);
    }

    private static Font chargerPolice() {
        try (InputStream flux = ressource(POLICE_NORMALE)) {
            return flux == null ? null : Font.createFont(Font.TRUETYPE_FONT, flux);
        } catch (Exception | LinkageError e) {
            journal.warn("Police de facture introuvable : polices standard du PDF seulement", e);
            return null;
        }
    }

    /** Montant lisible : {@code 1 234 567 FCFA}, séparateur de milliers insécable. */
    public static String montant(long fcfa) {
        String chiffres = Long.toString(Math.abs(fcfa));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chiffres.length(); i++) {
            if (i > 0 && (chiffres.length() - i) % 3 == 0) {
                sb.append(ESPACE_INSECABLE);
            }
            sb.append(chiffres.charAt(i));
        }
        return (fcfa < 0 ? "-" : "") + sb + ESPACE_INSECABLE + "FCFA";
    }

    /** Taux en pourcentage lisible : 1800 → « 18 », 550 → « 5,5 ». */
    public static String taux(int tauxBp) {
        return tauxBp % 100 == 0 ? Integer.toString(tauxBp / 100)
                : "%d,%02d".formatted(tauxBp / 100, tauxBp % 100).replaceAll("0$", "");
    }

    private PieceImprimable nettoyer(PieceImprimable p, boolean complet) {
        List<PieceImprimable.Ligne> lignes = p.lignes().stream().map(l -> new PieceImprimable.Ligne(
                texte(complet, l.reference()), texte(complet, l.designation()), l.quantite(), l.prixUnitaireTtc(),
                l.remiseTtc(), l.totalTtc(), l.totalHt(), l.montantTva())).toList();
        PieceImprimable.Vendeur v = p.vendeur();
        PieceImprimable.Client c = p.client();
        return new PieceImprimable(texte(complet, p.titre()), texte(complet, p.numero()), p.dateEmission(),
                texte(complet, p.numeroCommande()), texte(complet, p.factureOrigine()), texte(complet, p.motif()),
                new PieceImprimable.Vendeur(texte(complet, v.raisonSociale()), texte(complet, v.ninea()), texte(complet, v.rccm()),
                        texte(complet, v.adresse())),
                new PieceImprimable.Client(texte(complet, c.nom()), texte(complet, c.telephone()), texte(complet, c.email()),
                        texte(complet, c.adresse())),
                texte(complet, p.modePaiement()), p.regimeTva(), p.tauxTvaBp(), texte(complet, p.mentionTva()), lignes,
                p.totalTtc(), p.totalHt(), p.totalTva());
    }

    private static String texte(boolean complet, String valeur) {
        return complet ? couvert(valeur) : encodable(valeur);
    }

    /** Texte écrivable avec la police incorporée : normalisé (NFC), sans caractère de contrôle ni écriture complexe. */
    static String couvert(String valeur) {
        if (valeur == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(valeur.length());
        Normalizer.normalize(valeur, Normalizer.Form.NFC).codePoints().forEach(point -> {
            if (point == '\n') {
                sb.append('\n');
            } else if (Character.isISOControl(point) || Character.isSpaceChar(point) && point != ' ' && point != 0xA0) {
                sb.append(' ');
            } else if (ECRITURES_COMPLEXES.contains(Character.UnicodeScript.of(point))
                    || POLICE_DE_COUVERTURE == null || !POLICE_DE_COUVERTURE.canDisplay(point)) {
                sb.append('?');
            } else {
                sb.append(Character.toChars(point));
            }
        });
        return sb.toString();
    }

    /** Repli : remplace par « ? » ce que les polices standard du PDF (Windows-1252) ne savent pas écrire. */
    static String encodable(String texte) {
        if (texte == null) {
            return null;
        }
        CharsetEncoder encodeur = JEU_PDF.newEncoder();
        StringBuilder sb = new StringBuilder(texte.length());
        texte.codePoints().forEach(point -> {
            String caractere = new String(Character.toChars(point));
            if (point < 0x20 && point != '\n') {
                sb.append(' ');
            } else if (encodeur.canEncode(caractere)) {
                sb.append(caractere);
            } else {
                sb.append('?');
            }
        });
        return sb.toString();
    }
}
