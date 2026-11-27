package sn.boutique.noyau.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Version texte dérivée du HTML des e-mails : liens conservés, mise en page retirée. */
class HtmlVersTexteTest {

    @Test
    void conserveLesLiensEtLesParagraphes() {
        String texte = HtmlVersTexte.convertir("""
                <html><head><title>Sujet</title><style>p { color: red }</style></head>
                <body><p>Bonjour <strong>Awa</strong>,</p>
                <p>Suivez votre commande : <a href="https://boutique.sn/suivi/abc">Suivre ma commande</a></p></body></html>
                """);

        assertThat(texte).isEqualTo("""
                Bonjour Awa,

                Suivez votre commande : Suivre ma commande (https://boutique.sn/suivi/abc)
                """);
    }

    @Test
    void unLienAffichePourSonAdresseNEstPasDuplique() {
        assertThat(HtmlVersTexte.convertir("<p><a href=\"https://boutique.sn/x\">https://boutique.sn/x</a></p>"))
                .isEqualTo("https://boutique.sn/x\n");
    }

    @Test
    void decodeLesEntitesEtMetEnFormeLesTableaux() {
        String texte = HtmlVersTexte.convertir("""
                <table><tr><td>Clavier &amp; souris</td><td>12&nbsp;000 FCFA</td></tr>
                <tr><td>Total</td><td>L&#39;ensemble&#233;</td></tr></table><!-- commentaire -->
                """);

        assertThat(texte).isEqualTo("Clavier & souris 12 000 FCFA\nTotal L'ensembleé\n");
    }

    @Test
    void retireLesScriptsEtGereLesSautsDeLigne() {
        assertThat(HtmlVersTexte.convertir("<p>A<br>B<br/>C</p><script>alert(1)</script>")).isEqualTo("A\nB\nC\n");
        assertThat(HtmlVersTexte.convertir(null)).isEqualTo("");
    }
}
