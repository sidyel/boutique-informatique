package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;

/**
 * Sitemap et robots.txt (ADR 0007). En test, {@code INDEXATION_AUTORISEE} vaut false : robots.txt interdit
 * tout le site ; le sitemap reste servi (il ne liste que des pages publiées).
 */
class ReferencementSitemapTest extends CatalogueTestBase {

    @Test
    void robotsTxtInterditToutLeSiteHorsProduction() throws Exception {
        String robots = mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andReturn().getResponse().getContentAsString();
        assertThat(robots).isEqualTo("User-agent: *\nDisallow: /\n");
    }

    @Test
    void leSitemapNeListeQueLesPagesPubliees() throws Exception {
        Categorie active = categorie("Ecrans", null, 0, true);
        Categorie inactive = categorie("Minitels", null, 0, false);
        Marque marque = marque("Dell");
        Marque sansProduit = marque("Wang");
        Produit publie = produit("Dell P2425", marque, active, true);
        Produit masque = produit("Dell P2419", marque, active, false);
        Produit retire = produit("Dell P2217", marque, active, false);
        enTransaction(em -> {
            em.find(Produit.class, retire.getId()).retirer(Instant.parse("2026-11-20T10:00:00Z"), null);
            return null;
        });

        String index = lireXml("/sitemap.xml");
        assertThat(index).contains("<sitemapindex", "/sitemap-pages.xml</loc>", "/sitemap-categories.xml</loc>",
                "/sitemap-marques.xml</loc>", "/sitemap-produits-1.xml</loc>");

        String produits = lireXml("/sitemap-produits-1.xml");
        assertThat(produits).contains("/produit/" + publie.getSlug() + "</loc><lastmod>")
                .doesNotContain("/produit/" + masque.getSlug() + "<", "/produit/" + retire.getSlug() + "<");
        assertThat(lireXml("/sitemap-categories.xml")).contains("/categorie/" + active.getSlug() + "<")
                .doesNotContain("/categorie/" + inactive.getSlug() + "<");
        assertThat(lireXml("/sitemap-marques.xml")).contains("/marque/" + marque.getSlug() + "<")
                .doesNotContain("/marque/" + sansProduit.getSlug() + "<");
        assertThat(lireXml("/sitemap-pages.xml")).contains("<loc>").contains("/</loc>");
    }

    @Test
    void unFichierInconnuRepond404() throws Exception {
        for (String chemin : new String[] {"/sitemap-inconnu.xml", "/sitemap-produits-0.xml", "/sitemap-produits-9.xml"}) {
            mockMvc.perform(get(chemin))
                    .andExpect(status().isNotFound());
        }
    }

    private String lireXml(String chemin) throws Exception {
        return mockMvc.perform(get(chemin))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andReturn().getResponse().getContentAsString();
    }
}
