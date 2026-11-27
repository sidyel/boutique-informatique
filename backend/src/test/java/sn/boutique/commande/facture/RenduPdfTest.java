package sn.boutique.commande.facture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import sn.boutique.contenu.modele.RegimeTva;

/** Rendu PDF des factures et avoirs : contenu, poids, régimes de TVA, caractères non encodables. */
class RenduPdfTest {

    private static final String NBSP = " ";

    private static RenduPdfService service() {
        var resolveur = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolveur.setPrefix("templates/");
        resolveur.setSuffix(".html");
        resolveur.setTemplateMode("HTML");
        resolveur.setCharacterEncoding("UTF-8");
        var moteur = new org.thymeleaf.spring6.SpringTemplateEngine();
        moteur.setTemplateResolver(resolveur);
        return new RenduPdfService(moteur);
    }

    private static PieceImprimable.Vendeur vendeur() {
        return new PieceImprimable.Vendeur("Boutique Informatique SARL", "0012345 2G2", "SN-DKR-2026-B-1234",
                "Avenue Cheikh Anta Diop, Dakar");
    }

    private static PieceImprimable.Client client() {
        return new PieceImprimable.Client("Awa Diop", "+221781234567", "awa@exemple.sn", "Médina, Rue 10 x 11, Dakar");
    }

    private static PieceImprimable facture(RegimeTva regime, List<PieceImprimable.Ligne> lignes, long ttc, long ht,
                                           long tva) {
        return new PieceImprimable("Facture", "FAC-2026-000001", Instant.parse("2026-11-27T10:00:00Z"),
                "CMD-2026-000042", null, null, vendeur(), client(), "À la livraison", regime, 1800,
                "TVA non applicable", lignes, ttc, ht, tva);
    }

    private static String texte(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void uneFactureSansTvaPorteLesMentionsLesLignesEtLeTotal() throws IOException {
        var lignes = List.of(
                new PieceImprimable.Ligne("ASUS-A15", "PC portable ASUS TUF A15", 1, 450_000, 0, 450_000, 450_000, 0),
                new PieceImprimable.Ligne(null, "Livraison à domicile", 1, 2_000, 0, 2_000, 2_000, 0));

        byte[] pdf = service().rendre(facture(RegimeTva.NON_APPLICABLE, lignes, 452_000, 452_000, 0));
        String texte = texte(pdf);

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(texte).contains("FACTURE", "FAC-2026-000042".replace("000042", "000001"), "27/11/2026",
                "CMD-2026-000042", "Boutique Informatique SARL", "NINEA : 0012345 2G2", "RCCM : SN-DKR-2026-B-1234",
                "Avenue Cheikh Anta Diop", "Awa Diop", "+221781234567", "ASUS-A15", "PC portable ASUS TUF A15",
                "Livraison à domicile", "450" + NBSP + "000" + NBSP + "FCFA", "452" + NBSP + "000" + NBSP + "FCFA",
                "TVA non applicable", "Total à payer");
        assertThat(texte).doesNotContain("Total HT");
    }

    @Test
    void uneFactureAssujettieMontreLeHtEtLaTva() throws IOException {
        var lignes = List.of(new PieceImprimable.Ligne("A", "Clavier", 2, 59_000, 0, 118_000, 100_000, 18_000));

        String texte = texte(service().rendre(facture(RegimeTva.ASSUJETTI, lignes, 118_000, 100_000, 18_000)));

        assertThat(texte).contains("Total HT", "100" + NBSP + "000" + NBSP + "FCFA", "TVA 18 %",
                "18" + NBSP + "000" + NBSP + "FCFA", "Total TTC");
        assertThat(texte).doesNotContain("TVA non applicable");
    }

    @Test
    void unAvoirReferenceLaFactureEtLeMotif() throws IOException {
        var lignes = List.of(new PieceImprimable.Ligne("A", "Clavier", 1, 59_000, 0, 59_000, 59_000, 0));
        var avoir = new PieceImprimable("Avoir", "AV-2026-000001", Instant.parse("2026-11-28T10:00:00Z"),
                "CMD-2026-000042", "FAC-2026-000001", "Article défectueux", vendeur(), client(), "À la livraison",
                RegimeTva.NON_APPLICABLE, 1800, "TVA non applicable", lignes, 59_000, 59_000, 0);

        String texte = texte(service().rendre(avoir));

        assertThat(texte).contains("AVOIR", "AV-2026-000001", "Avoir sur la facture FAC-2026-000001",
                "Motif : Article défectueux");
    }

    @Test
    void lesCaracteresQueLesPolicesStandardNeSaventPasEcrireNeFontPasEchouerLEmission() throws IOException {
        var client = new PieceImprimable.Client("محمد Ndiaye 😀", "+221781234567", null, null);
        var lignes = List.of(new PieceImprimable.Ligne("A", "Souris « sans fil » – 2,4 GHz", 1, 9_000, 0, 9_000, 9_000, 0));
        var piece = new PieceImprimable("Facture", "FAC-2026-000002", Instant.parse("2026-11-27T10:00:00Z"), null, null,
                null, vendeur(), client, "En ligne", RegimeTva.NON_APPLICABLE, 1800, "TVA non applicable", lignes,
                9_000, 9_000, 0);

        String texte = texte(service().rendre(piece));

        assertThat(texte).contains("Ndiaye", "« sans fil » – 2,4 GHz", "?");
    }

    private byte[] pdfPourClient(String nom, String adresse, String designation) {
        var client = new PieceImprimable.Client(nom, "+221781234567", "client@exemple.sn", adresse);
        var lignes = List.of(new PieceImprimable.Ligne("REF-1", designation, 1, 9_000, 0, 9_000, 9_000, 0));
        return service().rendre(new PieceImprimable("Facture", "FAC-2026-000003", Instant.parse("2026-11-27T10:00:00Z"),
                "CMD-2026-000043", null, null, vendeur(), client, "En ligne", RegimeTva.NON_APPLICABLE, 1800,
                "TVA non applicable", lignes, 9_000, 9_000, 0));
    }

    @Test
    void lesAccentsLesApostrophesTypographiquesEtLesLettresWolofSontEcritsTelsQuels() throws IOException {
        byte[] pdf = pdfPourClient("Ibrahima Ndiaye-Sène, Mbaye Ŋgom",
                "Rue 10 x 11, Médina – Dakar ; Fass, Mermoz ’Villa n°5’ ; Ñandé ë ç è Ɓ Ɗ Ŋ ŋ",
                "Clavier « AZERTY » d’origine – l’Ñ de España, ç, ë");

        String texte = texte(pdf);

        assertThat(texte).contains("Ndiaye-Sène", "Ŋgom", "Médina – Dakar", "’Villa", "n°5’", "Ñandé ë ç è Ɓ Ɗ Ŋ ŋ",
                "« AZERTY » d’origine", "l’Ñ de España");
        assertThat(texte).doesNotContain("?");
        assertThat(pdf.length).as("police incorporée en sous-ensemble : le PDF reste léger").isLessThan(100 * 1024);
    }

    @Test
    void unNomEnArabeEstRemplacePropermentParUnPointDInterrogationSansFaireEchouerLEmission() throws IOException {
        byte[] pdf = pdfPourClient("محمد الأمين", "شارع الحبيب بورقيبة, Dakar", "Routeur Wi-Fi 6");

        String texte = texte(pdf);

        assertThat(texte).contains("???", "Dakar", "Routeur Wi-Fi 6");
        assertThat(texte).doesNotContain("محمد");
        assertThat(pdf.length).isLessThan(100 * 1024);
    }

    @Test
    void desEntreesAbsurdesNeFontJamaisEchouerLEmission() {
        String[] entrees = {"", " ", "\uD83D", "😀😀😀", "\u0000\u0001\u0007‮ reversé", "a​b‏c",
                "漢字かな한국어", "x".repeat(5_000), "Ünï̈cödé combiné é", "<b>&amp; \"quote\" 'apostrophe'</b>",
                "\t\r\n tabulations"};
        for (String entree : entrees) {
            byte[] pdf = pdfPourClient(entree, entree, entree);
            assertThat(pdf).isNotEmpty();
            assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        }
    }

    @Test
    void lesTextesSontNormalisesEtLesEcrituresComplexesRemplacees() {
        assertThat(RenduPdfService.couvert("é")).isEqualTo("é");
        assertThat(RenduPdfService.couvert("Aïssatou Ŋ")).isEqualTo("Aïssatou Ŋ");
        assertThat(RenduPdfService.couvert("مرحبا")).isEqualTo("?????");
        assertThat(RenduPdfService.couvert("ก")).isEqualTo("?");
        assertThat(RenduPdfService.couvert("a\u0000b")).isEqualTo("a b");
        assertThat(RenduPdfService.couvert(null)).isNull();
        // Repli (polices standard) : Windows-1252 conservé, le reste en « ? »
        assertThat(RenduPdfService.encodable("é « » – ŋ")).isEqualTo("é « » – ?");
    }

    @Test
    void uneFactureDeVingtLignesResteLegere() throws IOException {
        List<PieceImprimable.Ligne> lignes = new ArrayList<>();
        long total = 0;
        for (int i = 1; i <= 20; i++) {
            lignes.add(new PieceImprimable.Ligne("SKU-" + i, "Article de démonstration numéro " + i, 1, 10_000L * i, 0,
                    10_000L * i, 10_000L * i, 0));
            total += 10_000L * i;
        }

        byte[] pdf = service().rendre(facture(RegimeTva.NON_APPLICABLE, lignes, total, total, 0));

        assertThat(pdf.length).as("poids du PDF en octets").isLessThan(100 * 1024);
        assertThat(texte(pdf)).contains("SKU-20", "Article de démonstration numéro 20");
    }

    @Test
    void lesMontantsEtLesTauxSontFormatesALaFrancaise() {
        assertThat(RenduPdfService.montant(0)).isEqualTo("0" + NBSP + "FCFA");
        assertThat(RenduPdfService.montant(1_234_567)).isEqualTo("1" + NBSP + "234" + NBSP + "567" + NBSP + "FCFA");
        assertThat(RenduPdfService.taux(1800)).isEqualTo("18");
        assertThat(RenduPdfService.taux(550)).isEqualTo("5,5");
        assertThat(RenduPdfService.taux(1925)).isEqualTo("19,25");
    }
}
