package sn.boutique.noyau.html;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import sn.boutique.catalogue.modele.Produit;

/** Nettoyage par liste blanche de la description longue des produits. */
class NettoyeurHtmlTest {

    @Test
    void laMiseEnFormeUtileEstConservee() {
        String html = "<p>Portable <strong>léger</strong> et <em>rapide</em></p>"
                + "<ul><li>SSD NVMe</li><li>Wi-Fi 6</li></ul><ol><li>Un</li></ol><br>";

        assertThat(NettoyeurHtml.descriptionProduit(html)).isEqualTo(
                "<p>Portable <strong>léger</strong> et <em>rapide</em></p>"
                        + "<ul><li>SSD NVMe</li><li>Wi-Fi 6</li></ul><ol><li>Un</li></ol><br />");
    }

    @Test
    void lesScriptsStylesEtAttributsDangereuxSontRetires() {
        String html = "<p onclick=\"voler()\" style=\"color:red\" class=\"x\">Texte</p>"
                + "<script>alert(1)</script><iframe src=\"https://exemple.sn\"></iframe>"
                + "<img src=x onerror=alert(1)><a href=\"javascript:alert(1)\">piège</a>";

        String propre = NettoyeurHtml.descriptionProduit(html);

        assertThat(propre).isEqualTo("<p>Texte</p>piège");
    }

    @Test
    void lesTitresDescendentSousLeNomDuProduitEtLesLiensSontEnNofollow() {
        assertThat(NettoyeurHtml.descriptionProduit("<h1>Points forts</h1><h2>Écran</h2><h4>Détail</h4>"))
                .isEqualTo("<h3>Points forts</h3><h3>Écran</h3><h4>Détail</h4>");
        assertThat(NettoyeurHtml.descriptionProduit("<a href=\"https://www.asus.com/fr\" target=\"_blank\">Asus</a>"))
                .isEqualTo("<a href=\"https://www.asus.com/fr\" rel=\"nofollow\">Asus</a>");
    }

    @Test
    void uneDescriptionVideDevientNull() {
        assertThat(NettoyeurHtml.descriptionProduit(null)).isNull();
        assertThat(NettoyeurHtml.descriptionProduit("   ")).isNull();
        assertThat(NettoyeurHtml.descriptionProduit("<script>seul</script>")).isNull();
    }

    @Test
    void leProduitNettoieSaDescriptionALEcriture() throws Exception {
        var constructeur = Produit.class.getDeclaredConstructor();
        constructeur.setAccessible(true);
        Produit produit = constructeur.newInstance();

        produit.setDescriptionLongue("<p>Bon</p><script>alert(1)</script>");

        assertThat(produit.getDescriptionLongue()).isEqualTo("<p>Bon</p>");
    }
}
