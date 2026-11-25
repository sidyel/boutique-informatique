package sn.boutique.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import sn.boutique.noyau.erreur.RegleMetierException;

/**
 * Nettoyage des SVG : chaque attaque vise le domaine « evil.example » ou exécute alert() ; la sortie ne doit
 * jamais en contenir la moindre trace, et le dessin légitime doit être conservé.
 */
class NettoyeurSvgTest {

    private static final String DEBUT = "<svg xmlns=\"http://www.w3.org/2000/svg\" "
            + "xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 100 40\">";
    private static final String FIN = "<rect id=\"fond\" width=\"100\" height=\"40\" fill=\"#C2410C\"/></svg>";

    private final NettoyeurSvg nettoyeur = new NettoyeurSvg();

    // --- Ce qui est conservé ---------------------------------------------------------------------------

    @Test
    void unLogoLegitimeEstConserve() {
        String sortie = nettoyer("""
                <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 120 40" width="120" height="40">
                  <title>Boutique</title>
                  <defs>
                    <linearGradient id="degrade" x1="0" x2="1"><stop offset="0" stop-color="#C2410C"/><stop offset="1" stop-color="#9A3412"/></linearGradient>
                    <path id="forme" d="M0 0 L10 10 Z"/>
                  </defs>
                  <style>.texte { fill: url(#degrade); font-family: 'Space Grotesk'; }</style>
                  <g transform="translate(2 2)"><use href="#forme"/><use xlink:href="#forme" x="12"/></g>
                  <text class="texte" x="30" y="25" style="font-weight:700">Boutique</text>
                  <image width="8" height="8" href="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="/>
                </svg>""");

        Document document = lire(sortie);
        assertThat(document.getDocumentElement().getAttribute("viewBox")).isEqualTo("0 0 120 40");
        assertThat(elements(document, "linearGradient")).isEqualTo(1);
        assertThat(elements(document, "stop")).isEqualTo(2);
        assertThat(elements(document, "use")).isEqualTo(2);
        assertThat(sortie).contains("href=\"#forme\"").contains("xlink:href=\"#forme\"")
                .contains("fill: url(#degrade)").contains("Boutique").contains("data:image/png;base64,")
                .contains("style=\"font-weight:700\"");
    }

    // --- Scripts et gestionnaires d'événements -----------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<script xlink:href=\"https://evil.example/x.js\"/>",
            "<script><![CDATA[alert(1)]]></script>",
            "<Script>alert(1)</Script>",
            "<rect onload=\"alert(1)\" onclick=\"alert(1)\" width=\"1\" height=\"1\"/>",
            "<g onmouseover=\"alert(1)\"><circle r=\"2\" ONLOAD=\"alert(1)\"/></g>",
            "<foreignObject><iframe xmlns=\"http://www.w3.org/1999/xhtml\" src=\"https://evil.example\"/></foreignObject>",
            "<a href=\"javascript:alert(1)\"><rect width=\"5\" height=\"5\"/></a>",
            "<a xlink:href=\"https://evil.example\"><text>clic</text></a>",
            "<iframe src=\"https://evil.example\"/>",
            "<handler xmlns:ev=\"http://www.w3.org/2001/xml-events\" ev:event=\"load\">alert(1)</handler>"})
    void scriptsEtEvenementsSontRetires(String attaque) {
        verifierNeutralise(attaque);
    }

    // --- Animations SMIL ------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "<a><animate attributeName=\"href\" values=\"javascript:alert(1)\"/><text>clic</text></a>",
            "<use href=\"#fond\"><animate attributeName=\"href\" to=\"https://evil.example/x.svg#a\" begin=\"0s\"/></use>",
            "<rect width=\"1\" height=\"1\"><set attributeName=\"onmouseover\" to=\"alert(1)\"/></rect>",
            "<rect width=\"1\" height=\"1\"><set attributeName=\"xlink:href\" to=\"javascript:alert(1)\"/></rect>",
            "<g><animateTransform attributeName=\"transform\" type=\"rotate\" from=\"0\" to=\"360\" onbegin=\"alert(1)\"/></g>",
            "<circle r=\"3\"><animateMotion path=\"M0 0 L10 10\" onend=\"alert(1)\"/></circle>",
            "<rect width=\"1\" height=\"1\"><animate attributeName=\"fill\" values=\"red;blue\" onrepeat=\"alert(1)\"/></rect>",
            "<discard begin=\"0s\" onbegin=\"alert(1)\"/>"})
    void lesAnimationsSmilSontRetirees(String attaque) {
        String sortie = verifierNeutralise(attaque);
        assertThat(sortie.toLowerCase(Locale.ROOT)).doesNotContain("<animate").doesNotContain("<set")
                .doesNotContain("<discard");
    }

    // --- Références externes et data: -------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "<use href=\"https://evil.example/sprite.svg#icone\"/>",
            "<use xlink:href=\"//evil.example/sprite.svg#icone\"/>",
            "<use href=\"sprite-evil.example.svg#icone\"/>",
            "<use href=\"data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9ImFsZXJ0KDEpIi8+#x\"/>",
            "<use href=\" &#106;avascript:alert(1)\"/>",
            "<image href=\"https://evil.example/pixel.png\" width=\"1\" height=\"1\"/>",
            "<image href=\"data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9ImFsZXJ0KDEpIi8+\" width=\"1\" height=\"1\"/>",
            "<image xlink:href=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\" width=\"1\" height=\"1\"/>",
            "<image href=\"data:image/png,évil.example\" width=\"1\" height=\"1\"/>",
            "<rect fill=\"url(https://evil.example/f.svg#g)\" width=\"1\" height=\"1\"/>",
            "<rect fill=\"url( 'https://evil.example/f.svg#g' )\" width=\"1\" height=\"1\"/>",
            "<rect filter=\"url(//evil.example/f.svg#flou)\" width=\"1\" height=\"1\"/>",
            "<rect mask=\"url(data:image/svg+xml;base64,PHN2Zy8+)\" width=\"1\" height=\"1\"/>",
            "<linearGradient id=\"d\" href=\"https://evil.example/g.svg#d\"/>",
            "<textPath href=\"https://evil.example/p.svg#c\">texte</textPath>",
            "<feImage href=\"https://evil.example/i.png\"/>"})
    void lesReferencesExternesEtLesDataNonImagesSontRetirees(String attaque) {
        String sortie = verifierNeutralise(attaque);
        assertThat(sortie).doesNotContain("data:image/svg").doesNotContain("data:text");
    }

    // --- CSS ----------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "<style>@import url(https://evil.example/x.css);</style>",
            "<style>@import 'https://evil.example/x.css';</style>",
            "<style>@IMPORT\n'https://evil.example/x.css';</style>",
            "<style>rect { fill: url(https://evil.example/f.svg#g); }</style>",
            "<style>rect { background: url( \"//evil.example/p.png\" ); }</style>",
            "<style>@font-face { font-family: x; src: url(https://evil.example/f.woff2); }</style>",
            "<style>rect { background: url(javascript:alert(1)); }</style>",
            "<style>rect { b\\61 ckground: u\\72 l(https://evil.example/p.png); }</style>",
            "<style>@\\69mport 'https://evil.example/x.css';</style>",
            "<style>rect { behavior: url(evil.example.htc); width: expression(alert(1)); }</style>",
            "<style><![CDATA[ @import url(https://evil.example/x.css); ]]></style>",
            "<style>rect { fill: url(data:image/svg+xml;base64,PHN2Zy8+) }</style>",
            "<rect style=\"fill:url(https://evil.example/f.svg#g)\" width=\"1\" height=\"1\"/>",
            "<rect style=\"background:url(&#106;avascript:alert(1))\" width=\"1\" height=\"1\"/>",
            "<rect style=\"width:expression(alert(1))\" width=\"1\" height=\"1\"/>"})
    void leCssQuiChargeOuExecuteEstRetire(String attaque) {
        String sortie = verifierNeutralise(attaque);
        assertThat(sortie.toLowerCase(Locale.ROOT)).doesNotContain("@import").doesNotContain("expression(")
                .doesNotContain("behavior");
    }

    // --- Entités, commentaires, espaces de noms ---------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            // Billion laughs
            "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY a \"rire\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;\">"
                    + "<!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;\">]><svg xmlns=\"http://www.w3.org/2000/svg\"><text>&c;</text></svg>",
            // XXE : lecture d'un fichier du serveur
            "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                    + "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>&xxe;</text></svg>",
            // XXE vers le réseau (SSRF) et DTD externe
            "<?xml version=\"1.0\"?><!DOCTYPE svg SYSTEM \"https://evil.example/svg.dtd\">"
                    + "<svg xmlns=\"http://www.w3.org/2000/svg\"/>",
            "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY % p SYSTEM \"https://evil.example/p.dtd\"> %p;]>"
                    + "<svg xmlns=\"http://www.w3.org/2000/svg\"/>",
            // Simple DOCTYPE, sans entité : refusé lui aussi
            "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">"
                    + "<svg xmlns=\"http://www.w3.org/2000/svg\"/>"})
    void toutDocumentAvecDtdEstRefuse(String svg) {
        assertThatThrownBy(() -> nettoyer(svg)).isInstanceOf(RegleMetierException.class)
                .hasFieldOrPropertyWithValue("code", "SVG_INVALIDE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<!--[if IE]><script>alert(1)</script><![endif]-->",
            "<!-- <script src=\"https://evil.example/x.js\"></script> -->",
            "<?xml-stylesheet type=\"text/xsl\" href=\"https://evil.example/x.xsl\"?>",
            "<x:script xmlns:x=\"http://www.w3.org/1999/xhtml\">alert(1)</x:script>",
            "<html:iframe xmlns:html=\"http://www.w3.org/1999/xhtml\" src=\"https://evil.example\"/>",
            "<e:rect xmlns:e=\"https://evil.example/ns\" width=\"1\" height=\"1\"/>",
            "<rect xmlns:e=\"https://evil.example/ns\" e:onload=\"alert(1)\" width=\"1\" height=\"1\"/>",
            "<rect xml:base=\"https://evil.example/\" width=\"1\" height=\"1\"/>",
            "<sodipodi:namedview xmlns:sodipodi=\"http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd\" id=\"evil.example\"/>",
            "<metadata><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">evil.example</rdf:RDF></metadata>"})
    void commentairesInstructionsEtEspacesDeNomsInattendusSontRetires(String attaque) {
        verifierNeutralise(attaque);
    }

    @Test
    void uneRacineAutreQueSvgOuUnXmlMalFormeEstRefuse() {
        for (String invalide : new String[] {
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><script>alert(1)</script></html>",
                "<svg xmlns=\"https://evil.example/ns\"><rect/></svg>",
                "<svg><rect/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect></svg>",
                "pas du xml"}) {
            assertThatThrownBy(() -> nettoyer(invalide)).as(invalide)
                    .hasFieldOrPropertyWithValue("code", "SVG_INVALIDE");
        }
    }

    @Test
    void unDocumentTropProfondEstRefuse() {
        String svg = DEBUT + "<g>".repeat(100) + "</g>".repeat(100) + "</svg>";
        assertThatThrownBy(() -> nettoyer(svg)).hasFieldOrPropertyWithValue("code", "SVG_INVALIDE");
    }

    @Test
    void lesRetraitsSontListesPourLeJournal() {
        NettoyeurSvg.SvgNettoye resultat = nettoyeur.nettoyer((DEBUT
                + "<script>alert(1)</script><rect onclick=\"alert(1)\" width=\"1\" height=\"1\"/>" + FIN)
                .getBytes(StandardCharsets.UTF_8));

        assertThat(resultat.retraits()).contains("<script>", "attribut onclick");
    }

    // ------------------------------------------------------------------------------------------------

    /** Insère l'attaque dans un logo valide, vérifie qu'il n'en reste rien et que le dessin est conservé. */
    private String verifierNeutralise(String attaque) {
        String sortie = nettoyer(DEBUT + attaque + FIN);
        String minuscules = sortie.toLowerCase(Locale.ROOT);
        assertThat(minuscules).as("sortie pour %s", attaque)
                .doesNotContain("evil.example")
                .doesNotContain("alert")
                .doesNotContain("javascript")
                .doesNotContain("<script")
                .doesNotContain("foreignobject")
                .doesNotContain("iframe")
                .doesNotContain("<!--")
                .doesNotContain("<?xml-stylesheet")
                .doesNotContainPattern("\\son[a-z]+=");
        Document document = lire(sortie);
        assertThat(document.getElementById("fond") != null || elements(document, "rect") >= 1)
                .as("le dessin légitime est conservé").isTrue();
        return sortie;
    }

    private String nettoyer(String svg) {
        return new String(nettoyeur.nettoyer(svg.getBytes(StandardCharsets.UTF_8)).contenu(), StandardCharsets.UTF_8);
    }

    private static Document lire(String xml) {
        try {
            DocumentBuilderFactory fabrique = DocumentBuilderFactory.newInstance();
            fabrique.setNamespaceAware(true);
            return fabrique.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError("La sortie n'est pas du XML valide : " + xml, e);
        }
    }

    private static int elements(Document document, String nom) {
        NodeList liste = document.getElementsByTagNameNS(NettoyeurSvg.NS_SVG, nom);
        int nombre = 0;
        for (int i = 0; i < liste.getLength(); i++) {
            if (liste.item(i) instanceof Element) {
                nombre++;
            }
        }
        return nombre;
    }
}
