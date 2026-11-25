package sn.boutique.noyau.html;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * Nettoyage du HTML saisi par l'équipe (description longue des produits), par liste blanche : tout ce qui
 * n'est pas listé est retiré (scripts, styles, attributs d'événement, iframes, images, classes…).
 * <p>
 * Appliqué à l'écriture ({@code Produit#setDescriptionLongue}) et à la lecture de la fiche (données
 * enregistrées avant ce nettoyage). Le site le refait aussi côté navigateur (nettoyage d'Angular).
 */
public final class NettoyeurHtml {

    /**
     * Paragraphes, listes, gras et italique, intertitres, citations, tableaux simples, liens web.
     * Les titres h1 et h2 deviennent h3 : la fiche a déjà son h1 (nom du produit) et ses sections en h2.
     * Les liens reçoivent rel="nofollow" (aucun transfert de réputation vers un site saisi à la main).
     */
    private static final PolicyFactory DESCRIPTION = new HtmlPolicyBuilder()
            .allowElements("p", "br", "ul", "ol", "li", "strong", "b", "em", "i", "h3", "h4", "blockquote",
                    "table", "thead", "tbody", "tr", "th", "td")
            .allowElements((nom, attributs) -> "h3", "h1", "h2")
            .allowElements("a")
            .allowAttributes("href").onElements("a")
            .allowStandardUrlProtocols()
            .requireRelNofollowOnLinks()
            .toFactory();

    private NettoyeurHtml() {
    }

    /** HTML nettoyé, ou {@code null} s'il ne reste rien d'affichable. */
    public static String descriptionProduit(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String propre = DESCRIPTION.sanitize(html).strip();
        return propre.isEmpty() ? null : propre;
    }
}
