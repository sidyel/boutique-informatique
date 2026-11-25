package sn.boutique.media;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.stereotype.Component;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import sn.boutique.noyau.erreur.RegleMetierException;

/**
 * Nettoyage des SVG envoyés (logo, favicon) par <strong>liste blanche</strong> : le document de sortie est
 * reconstruit avec les seuls éléments et attributs autorisés ; tout le reste est retiré avec son contenu.
 * <p>
 * Retirés, entre autres : {@code <script>}, {@code <foreignObject>}, liens {@code <a>}, attributs {@code on*},
 * animations SMIL ({@code <animate>}, {@code <set>}, {@code <animateTransform>}… qui pourraient réécrire un
 * {@code href} en {@code javascript:}), {@code href} externes (y compris {@code <use>} vers un autre fichier),
 * {@code data:} autres que des images PNG, JPEG ou WebP en base64, CSS avec {@code @import}, {@code url()}
 * externe ou échappements, commentaires (dont les commentaires conditionnels), instructions de traitement,
 * éléments et attributs d'espaces de noms inattendus (Inkscape, RDF…).
 * <p>
 * Refusés : toute DTD (donc toute entité : XXE, « billion laughs »), XML mal formé, racine autre que
 * {@code <svg>}, document trop profond ou trop grand.
 * <p>
 * Défense en profondeur : le SVG est toujours affiché par {@code <img src>}, jamais injecté dans le HTML
 * (ADR 0005), contexte où les navigateurs n'exécutent aucun script.
 */
@Component
public class NettoyeurSvg {

    static final String NS_SVG = "http://www.w3.org/2000/svg";
    static final String NS_XLINK = "http://www.w3.org/1999/xlink";

    private static final int PROFONDEUR_MAX = 64;
    private static final int ELEMENTS_MAX = 20_000;

    private static final Set<String> ELEMENTS = Set.of(
            "svg", "g", "defs", "title", "desc", "symbol", "use", "style",
            "path", "rect", "circle", "ellipse", "line", "polyline", "polygon", "text", "tspan", "textPath", "image",
            "linearGradient", "radialGradient", "stop", "clipPath", "mask", "pattern",
            "filter", "feGaussianBlur", "feOffset", "feBlend", "feColorMatrix", "feFlood", "feComposite", "feMerge",
            "feMergeNode", "feDropShadow");

    private static final Set<String> ATTRIBUTS = Set.of(
            "id", "class", "style", "transform", "version", "viewBox", "preserveAspectRatio", "width", "height",
            "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry", "fx", "fy", "fr", "d", "points",
            "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin",
            "stroke-miterlimit", "stroke-dasharray", "stroke-dashoffset", "stroke-opacity", "opacity", "color",
            "display", "visibility", "clip-path", "clip-rule", "clipPathUnits", "mask", "maskUnits",
            "maskContentUnits", "gradientUnits", "gradientTransform", "spreadMethod", "offset", "stop-color",
            "stop-opacity", "patternUnits", "patternContentUnits", "patternTransform", "font-family", "font-size",
            "font-weight", "font-style", "text-anchor", "dominant-baseline", "letter-spacing", "dx", "dy", "rotate",
            "textLength", "lengthAdjust", "startOffset", "filter", "filterUnits", "primitiveUnits", "in", "in2",
            "result", "stdDeviation", "mode", "operator", "k1", "k2", "k3", "k4", "values", "type", "flood-color",
            "flood-opacity", "media");

    /** Éléments dont le {@code href} peut viser une partie du même document ({@code #id}). */
    private static final Set<String> HREF_INTERNE = Set.of("use", "textPath", "linearGradient", "radialGradient",
            "pattern");

    private static final Pattern REFERENCE_INTERNE = Pattern.compile("#[A-Za-z_][A-Za-z0-9_.:-]{0,200}");
    private static final Pattern IMAGE_DATA = Pattern.compile(
            "data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=\\s]+", Pattern.CASE_INSENSITIVE);
    /** url( suivi d'autre chose qu'une référence interne : url(#degrade) est accepté, url(http…) non. */
    private static final Pattern URL_EXTERNE = Pattern.compile("url\\((?!['\"]?#)");
    private static final Pattern ESPACES_ET_CONTROLES = Pattern.compile("[\\s\\x00-\\x1f\\x7f]+");

    /** Résultat : le SVG reconstruit et ce qui a été retiré (pour le journal). */
    public record SvgNettoye(byte[] contenu, Set<String> retraits) {
    }

    public SvgNettoye nettoyer(byte[] contenu) {
        Document source = analyser(contenu);
        Element racine = source.getDocumentElement();
        if (!NS_SVG.equals(racine.getNamespaceURI()) || !"svg".equals(racine.getLocalName())) {
            throw refus("La racine du fichier n'est pas un élément <svg>.");
        }
        Document cible = nouveauDocument();
        Set<String> retraits = new TreeSet<>();
        int[] compteur = {0};
        cible.appendChild(copier(racine, cible, retraits, 0, compteur));
        return new SvgNettoye(ecrire(cible), retraits);
    }

    private Element copier(Element source, Document cible, Set<String> retraits, int profondeur, int[] compteur) {
        if (profondeur > PROFONDEUR_MAX || ++compteur[0] > ELEMENTS_MAX) {
            throw refus("Le fichier SVG est trop complexe.");
        }
        String nom = source.getLocalName();
        Element copie = cible.createElementNS(NS_SVG, nom);
        copierAttributs(source, copie, retraits);

        for (Node enfant = source.getFirstChild(); enfant != null; enfant = enfant.getNextSibling()) {
            switch (enfant.getNodeType()) {
                case Node.ELEMENT_NODE -> {
                    Element element = (Element) enfant;
                    if (NS_SVG.equals(element.getNamespaceURI()) && ELEMENTS.contains(element.getLocalName())) {
                        if ("style".equals(element.getLocalName()) && !cssSur(element.getTextContent())) {
                            retraits.add("<style> (CSS non autorisé)");
                        } else {
                            copie.appendChild(copier(element, cible, retraits, profondeur + 1, compteur));
                        }
                    } else {
                        retraits.add("<" + element.getNodeName() + ">");
                    }
                }
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> copie.appendChild(cible.createTextNode(enfant.getNodeValue()));
                case Node.COMMENT_NODE -> retraits.add("commentaire");
                default -> retraits.add("nœud " + enfant.getNodeName());
            }
        }
        return copie;
    }

    private void copierAttributs(Element source, Element copie, Set<String> retraits) {
        NamedNodeMap attributs = source.getAttributes();
        for (int i = 0; i < attributs.getLength(); i++) {
            Attr attribut = (Attr) attributs.item(i);
            String espace = attribut.getNamespaceURI();
            String nom = attribut.getLocalName() == null ? attribut.getName() : attribut.getLocalName();
            String valeur = attribut.getValue();

            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(espace)) {
                continue; // déclarations d'espaces de noms : le sérialiseur écrit les siennes
            }
            boolean href = "href".equals(nom) && (espace == null || NS_XLINK.equals(espace));
            if (href) {
                if (hrefAutorise(source.getLocalName(), valeur)) {
                    if (NS_XLINK.equals(espace)) {
                        copie.setAttributeNS(NS_XLINK, "xlink:href", valeur);
                    } else {
                        copie.setAttribute("href", valeur);
                    }
                } else {
                    retraits.add("href « " + abreger(valeur) + " » sur <" + source.getLocalName() + ">");
                }
            } else if (XMLConstants.XML_NS_URI.equals(espace) && "space".equals(nom)) {
                copie.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", valeur);
            } else if (espace == null && ATTRIBUTS.contains(nom) && valeurSure(nom, valeur)) {
                copie.setAttribute(nom, valeur);
            } else {
                retraits.add("attribut " + attribut.getName());
            }
        }
    }

    private static boolean hrefAutorise(String element, String valeur) {
        String href = valeur.strip();
        if (HREF_INTERNE.contains(element)) {
            return REFERENCE_INTERNE.matcher(href).matches();
        }
        if ("image".equals(element)) {
            return IMAGE_DATA.matcher(href).matches();
        }
        return false;
    }

    private static boolean valeurSure(String nom, String valeur) {
        String compacte = ESPACES_ET_CONTROLES.matcher(valeur).replaceAll("").toLowerCase(Locale.ROOT);
        if (compacte.contains("javascript:") || compacte.contains("vbscript:") || compacte.contains("data:")
                || URL_EXTERNE.matcher(compacte).find()) {
            return false;
        }
        return !"style".equals(nom) || cssSur(valeur);
    }

    /** CSS d'un attribut style ou d'un élément {@code <style>} : rien qui charge, exécute ou masque du contenu. */
    private static boolean cssSur(String css) {
        String compacte = ESPACES_ET_CONTROLES.matcher(css).replaceAll("").toLowerCase(Locale.ROOT);
        return !compacte.contains("@import") && !compacte.contains("\\") && !compacte.contains("<")
                && !compacte.contains("javascript:") && !compacte.contains("expression(")
                && !compacte.contains("behavior:") && !compacte.contains("-moz-binding")
                && !compacte.contains("data:") && !URL_EXTERNE.matcher(compacte).find();
    }

    private static Document analyser(byte[] contenu) {
        try {
            DocumentBuilderFactory fabrique = DocumentBuilderFactory.newInstance();
            fabrique.setNamespaceAware(true);
            fabrique.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // Aucune DTD : ni entité interne (« billion laughs ») ni externe (XXE)
            fabrique.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrique.setFeature("http://xml.org/sax/features/external-general-entities", false);
            fabrique.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            fabrique.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            fabrique.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrique.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            fabrique.setXIncludeAware(false);
            fabrique.setExpandEntityReferences(false);
            DocumentBuilder lecteur = fabrique.newDocumentBuilder();
            lecteur.setErrorHandler(null);
            return lecteur.parse(new ByteArrayInputStream(contenu));
        } catch (SAXException | IOException e) {
            throw refus("Le fichier SVG est invalide ou contient une DTD (" + e.getMessage() + ").");
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Analyseur XML mal configuré", e);
        }
    }

    private static Document nouveauDocument() {
        try {
            DocumentBuilderFactory fabrique = DocumentBuilderFactory.newInstance();
            fabrique.setNamespaceAware(true);
            return fabrique.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] ecrire(Document document) {
        try {
            TransformerFactory fabrique = TransformerFactory.newInstance();
            fabrique.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrique.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrique.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformateur = fabrique.newTransformer();
            transformateur.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformateur.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            transformateur.transform(new DOMSource(document), new StreamResult(sortie));
            return sortie.toByteArray();
        } catch (TransformerException e) {
            throw new IllegalStateException("Écriture du SVG nettoyé impossible", e);
        }
    }

    private static String abreger(String valeur) {
        return valeur.length() <= 40 ? valeur : valeur.substring(0, 40) + "…";
    }

    private static RegleMetierException refus(String message) {
        return new RegleMetierException("SVG_INVALIDE", message);
    }
}
