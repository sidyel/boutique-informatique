package sn.boutique.referencement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import sn.boutique.noyau.config.ProprietesBoutique;
import sn.boutique.referencement.service.SitemapService;

/** robots.txt selon INDEXATION_AUTORISEE (ADR 0007). Le cas « false » est vérifié de bout en bout ailleurs. */
class ReferencementControleurTest {

    private static ReferencementControleur controleur(boolean indexation) {
        return new ReferencementControleur(mock(SitemapService.class),
                new ProprietesBoutique("https://boutique.sn/", List.of("https://boutique.sn"), indexation));
    }

    @Test
    void enProductionRobotsTxtNeBloqueQueLesPagesPriveesEtAnnonceLeSitemap() {
        assertThat(controleur(true).robots().getBody()).isEqualTo("""
                User-agent: *
                Disallow: /admin
                Disallow: /compte
                Disallow: /panier
                Disallow: /commande
                Disallow: /suivi
                Disallow: /api/
                Disallow: /recherche

                Sitemap: https://boutique.sn/sitemap.xml
                """);
    }

    @Test
    void horsProductionRobotsTxtInterditToutLeSite() {
        assertThat(controleur(false).robots().getBody()).isEqualTo("User-agent: *\nDisallow: /\n");
    }
}
