package sn.boutique.commande.facture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Rend une facture ou un avoir en PDF à partir de {@link PieceImprimable} et du gabarit {@code pdf/piece.html}.
 * <p>
 * <strong>Polices standard du PDF</strong> (Helvetica, Courier) : rien à incorporer, un document de 20 lignes pèse
 * quelques Ko. Ces polices n'encodent que le jeu Windows-1252 (français complet) : tout autre caractère (nom d'un
 * client en écriture non latine, par exemple) est remplacé par « ? » avant le rendu plutôt que de faire échouer
 * l'émission. Les montants utilisent l'espace insécable U+00A0, présent dans ce jeu.
 */
@Service
public class RenduPdfService {

    private static final Charset JEU_PDF = Charset.forName("windows-1252");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)
            .withZone(ZoneOffset.UTC);
    private static final char ESPACE_INSECABLE = ' ';

    private final ITemplateEngine moteurModeles;

    public RenduPdfService(ITemplateEngine moteurModeles) {
        this.moteurModeles = moteurModeles;
    }

    public byte[] rendre(PieceImprimable piece) {
        Context contexte = new Context(Locale.FRANCE);
        contexte.setVariable("p", nettoyer(piece));
        contexte.setVariable("date", DATE.format(piece.dateEmission()));
        String html = moteurModeles.process("pdf/piece", contexte);
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream(16 * 1024)) {
            PdfRendererBuilder constructeur = new PdfRendererBuilder();
            constructeur.withHtmlContent(html, null);
            constructeur.toStream(sortie);
            constructeur.run();
            return sortie.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Génération du PDF impossible pour " + piece.numero(), e);
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

    private PieceImprimable nettoyer(PieceImprimable p) {
        List<PieceImprimable.Ligne> lignes = p.lignes().stream().map(l -> new PieceImprimable.Ligne(
                encodable(l.reference()), encodable(l.designation()), l.quantite(), l.prixUnitaireTtc(),
                l.remiseTtc(), l.totalTtc(), l.totalHt(), l.montantTva())).toList();
        PieceImprimable.Vendeur v = p.vendeur();
        PieceImprimable.Client c = p.client();
        return new PieceImprimable(encodable(p.titre()), encodable(p.numero()), p.dateEmission(),
                encodable(p.numeroCommande()), encodable(p.factureOrigine()), encodable(p.motif()),
                new PieceImprimable.Vendeur(encodable(v.raisonSociale()), encodable(v.ninea()), encodable(v.rccm()),
                        encodable(v.adresse())),
                new PieceImprimable.Client(encodable(c.nom()), encodable(c.telephone()), encodable(c.email()),
                        encodable(c.adresse())),
                encodable(p.modePaiement()), p.regimeTva(), p.tauxTvaBp(), encodable(p.mentionTva()), lignes,
                p.totalTtc(), p.totalHt(), p.totalTva());
    }

    /** Remplace par « ? » ce que les polices standard du PDF ne savent pas écrire. */
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
