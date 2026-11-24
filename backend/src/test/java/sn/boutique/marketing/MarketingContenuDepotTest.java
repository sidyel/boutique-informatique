package sn.boutique.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.boutique.support.Contraintes.contrainteViolee;
import static sn.boutique.support.FabriqueCatalogue.unique;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.NumeroCommande;
import sn.boutique.contenu.depot.ArticleDepot;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.modele.Article;
import sn.boutique.contenu.modele.ParametresSite;
import sn.boutique.marketing.depot.CodePromoDepot;
import sn.boutique.marketing.depot.CollectionDepot;
import sn.boutique.marketing.depot.JournalConversionDepot;
import sn.boutique.marketing.modele.Banniere;
import sn.boutique.marketing.modele.CodePromo;
import sn.boutique.marketing.modele.Collection;
import sn.boutique.marketing.modele.EmplacementBanniere;
import sn.boutique.marketing.modele.JournalConversion;
import sn.boutique.marketing.modele.PlateformeConversion;
import sn.boutique.marketing.modele.TypeCodePromo;
import sn.boutique.support.FabriqueCatalogue;
import sn.boutique.support.FabriqueComptes;
import sn.boutique.support.TestDepot;

@TestDepot
class MarketingContenuDepotTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private CodePromoDepot codePromoDepot;

    @Autowired
    private CollectionDepot collectionDepot;

    @Autowired
    private JournalConversionDepot journalDepot;

    @Autowired
    private ParametresSiteDepot parametresDepot;

    @Autowired
    private ArticleDepot articleDepot;

    private static String codeUnique() {
        return unique("TABASKI").toUpperCase(Locale.ROOT);
    }

    @Test
    void leCodePromoEstRetrouveQuelleQueSoitLaSaisie() {
        String code = codeUnique();
        CodePromo promo = new CodePromo(" " + code.toLowerCase(Locale.ROOT) + " ", TypeCodePromo.POURCENTAGE, 10);
        promo.getCategoriesCiblees().add(FabriqueCatalogue.categorie(em));
        codePromoDepot.saveAndFlush(promo);
        em.clear();

        CodePromo relu = codePromoDepot.trouverParCode(code.toLowerCase(Locale.ROOT)).orElseThrow();
        assertThat(relu.getCode()).isEqualTo(code);
        assertThat(relu.getCategoriesCiblees()).hasSize(1);
        assertThat(relu.estValideA(Instant.now())).isTrue();
    }

    @Test
    void unPourcentageSuperieurA100EstRefuse() {
        CodePromo promo = new CodePromo(codeUnique(), TypeCodePromo.POURCENTAGE, 150);

        assertThatThrownBy(() -> codePromoDepot.saveAndFlush(promo))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_code_promo_valeur"));
    }

    @Test
    void unCodeExpireNEstPlusValide() {
        CodePromo promo = new CodePromo(codeUnique(), TypeCodePromo.MONTANT_FIXE, 5_000);
        Instant maintenant = Instant.now();
        promo.setDateDebut(maintenant.minus(10, ChronoUnit.DAYS));
        promo.setDateFin(maintenant.minus(1, ChronoUnit.DAYS));

        assertThat(promo.estValideA(maintenant)).isFalse();
        assertThat(promo.estValideA(maintenant.minus(5, ChronoUnit.DAYS))).isTrue();
    }

    @Test
    void uneBanniereDEnTeteDoitViserUneCategorie() {
        Banniere sansCategorie = new Banniere(EmplacementBanniere.ENTETE_CATEGORIE,
                "https://exemple/o.webp", "https://exemple/m.webp", "Portables gaming");
        em.persist(FabriqueCatalogue.categorie(em));

        assertThatThrownBy(() -> {
            em.persist(sansCategorie);
            em.flush();
        }).satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_banniere_categorie"));
    }

    @Test
    void uneCollectionGardeLOrdreDesProduits() {
        Produit premier = FabriqueCatalogue.produit(em);
        Produit second = FabriqueCatalogue.produit(em);
        em.persist(premier);
        em.persist(second);
        Collection collection = new Collection("Black Friday", unique("black-friday"));
        collection.getProduits().add(second);
        collection.getProduits().add(premier);
        collection.setIndexable(false);
        collectionDepot.saveAndFlush(collection);
        em.clear();

        Collection relue = collectionDepot.findBySlugAndActifTrue(collection.getSlug()).orElseThrow();
        assertThat(relue.getProduits()).extracting(Produit::getId).containsExactly(second.getId(), premier.getId());
        assertThat(relue.isIndexable()).isFalse();
    }

    @Test
    void uneConversionNEstJamaisJournaliseeDeuxFois() {
        Commande commande = FabriqueComptes.commandeRetrait();
        em.persist(commande);
        String idEvenement = NumeroCommande.idEvenementAchat(commande.getNumero());
        journalDepot.saveAndFlush(new JournalConversion(commande, PlateformeConversion.META, "Purchase", idEvenement));

        assertThat(journalDepot.existsByPlateformeAndIdEvenement(PlateformeConversion.META, idEvenement)).isTrue();
        // Même événement vers Google Ads : autorisé
        journalDepot.saveAndFlush(new JournalConversion(commande, PlateformeConversion.GOOGLE_ADS, "purchase", idEvenement));

        JournalConversion doublon = new JournalConversion(commande, PlateformeConversion.META, "Purchase", idEvenement);
        assertThatThrownBy(() -> journalDepot.saveAndFlush(doublon))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("uk_journal_conversion_evenement"));
    }

    @Test
    void lesParametresDuSiteSontUneLigneUnique() {
        ParametresSite parametres = parametresDepot.lire();
        parametres.setWhatsapp("77 123 45 67");
        parametres.setIdGoogleTagManager("GTM-ABC1234");
        parametres.getLiensReseauxSociaux().put("facebook", "https://facebook.com/exemple");
        parametresDepot.saveAndFlush(parametres);
        em.clear();

        ParametresSite relus = parametresDepot.lire();
        assertThat(relus.getNomSite()).isEqualTo("Boutique informatique");
        assertThat(relus.getWhatsapp()).isEqualTo("+221771234567");
        assertThat(relus.getLiensReseauxSociaux()).containsEntry("facebook", "https://facebook.com/exemple");

        assertThatThrownBy(() -> em.createNativeQuery("insert into parametres_site (id, nom_site) values (2, 'Autre')")
                .executeUpdate())
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_parametres_site_ligne_unique"));
    }

    @Test
    void unIdentifiantDeSuiviMalSaisiEstRefuse() {
        ParametresSite parametres = parametresDepot.lire();
        parametres.setIdPixelMeta("fbq-123");

        assertThatThrownBy(() -> parametresDepot.saveAndFlush(parametres))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_parametres_site_pixel_meta"));
    }

    @Test
    void unArticlePublieGardeSaDateDePremierePublication() {
        Produit produit = FabriqueCatalogue.produit(em);
        em.persist(produit);
        Article article = new Article("Quel PC portable pour un étudiant à Dakar ?", unique("pc-etudiant"), "La rédaction");
        article.getProduitsLies().add(produit);
        Instant premiere = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        article.publier(premiere);
        article.depublier();
        article.publier(Instant.now());
        articleDepot.saveAndFlush(article);
        em.clear();

        Article relu = articleDepot.findBySlugAndPublieTrue(article.getSlug()).orElseThrow();
        assertThat(relu.getDatePublication()).isEqualTo(premiere);
        assertThat(relu.getProduitsLies()).hasSize(1);
    }
}
