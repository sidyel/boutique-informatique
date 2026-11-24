package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.boutique.support.FabriqueCatalogue.unique;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.boutique.catalogue.depot.HistoriqueSlugDepot;
import sn.boutique.catalogue.depot.ProduitDepot;
import sn.boutique.catalogue.depot.VarianteProduitDepot;
import sn.boutique.catalogue.modele.DefinitionCaracteristique;
import sn.boutique.catalogue.modele.HistoriqueSlug;
import sn.boutique.catalogue.modele.MediaProduit;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeCaracteristique;
import sn.boutique.catalogue.modele.TypeEntiteSlug;
import sn.boutique.catalogue.modele.TypeMedia;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.support.FabriqueCatalogue;
import sn.boutique.support.TestDepot;

@TestDepot
class CatalogueDepotTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private ProduitDepot produitDepot;

    @Autowired
    private VarianteProduitDepot varianteDepot;

    @Autowired
    private HistoriqueSlugDepot historiqueSlugDepot;

    @Test
    void lesDatesDAuditSontRempliesALEnregistrement() {
        Produit produit = FabriqueCatalogue.produit(em);
        produitDepot.saveAndFlush(produit);

        assertThat(produit.getId()).isNotNull();
        assertThat(produit.getDateCreation()).isNotNull();
        assertThat(produit.getDateModification()).isNotNull();
    }

    @Test
    void leSlugDuProduitEstUnique() {
        Produit premier = FabriqueCatalogue.produit(em);
        produitDepot.saveAndFlush(premier);

        Produit doublon = new Produit("Autre", premier.getSlug(), premier.getMarque(), premier.getCategorie());

        assertThatThrownBy(() -> produitDepot.saveAndFlush(doublon))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("uk_produit_slug"));
    }

    @Test
    void leSkuEstUnique() {
        Produit produit = FabriqueCatalogue.produit(em);
        String sku = unique("ASUS-FA507");
        produit.ajouterVariante(sku, "16 Go / 512 Go", 450_000, 3);
        produitDepot.saveAndFlush(produit);

        Produit autre = FabriqueCatalogue.produit(em);
        autre.ajouterVariante(sku, "8 Go / 512 Go", 399_000, 1);

        assertThatThrownBy(() -> produitDepot.saveAndFlush(autre))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("uk_variante_produit_sku"));
    }

    @Test
    void prixMinimumEtStockTotalSuiventLesVariantesActives() {
        Produit produit = FabriqueCatalogue.produit(em);
        VarianteProduit v16 = produit.ajouterVariante(unique("A15-16"), "16 Go / 512 Go", 450_000, 3);
        produit.ajouterVariante(unique("A15-32"), "32 Go / 1 To", 620_000, 2);
        VarianteProduit inactive = produit.ajouterVariante(unique("A15-8"), "8 Go / 256 Go", 300_000, 10);
        inactive.setActif(false);
        produitDepot.saveAndFlush(produit);
        em.refresh(produit);

        assertThat(produit.getPrixMinimum()).isEqualTo(450_000L);
        assertThat(produit.getStockTotal()).isEqualTo(5);

        // Baisse de prix et vente sur une variante
        v16.setPrix(429_000);
        v16.setStock(1);
        em.flush();
        em.refresh(produit);
        assertThat(produit.getPrixMinimum()).isEqualTo(429_000L);
        assertThat(produit.getStockTotal()).isEqualTo(3);

        // Réactivation de la variante la moins chère
        inactive.setActif(true);
        em.flush();
        em.refresh(produit);
        assertThat(produit.getPrixMinimum()).isEqualTo(300_000L);
        assertThat(produit.getStockTotal()).isEqualTo(13);

        // Suppression d'une variante
        produit.getVariantes().remove(inactive);
        em.flush();
        em.refresh(produit);
        assertThat(produit.getPrixMinimum()).isEqualTo(429_000L);
        assertThat(produit.getStockTotal()).isEqualTo(3);
    }

    @Test
    void unProduitSansVarianteActiveNAPasDePrixMinimum() {
        Produit produit = FabriqueCatalogue.produit(em);
        produit.ajouterVariante(unique("SKU"), "Unique", 99_000, 4).setActif(false);
        produitDepot.saveAndFlush(produit);
        em.refresh(produit);

        assertThat(produit.getPrixMinimum()).isNull();
        assertThat(produit.getStockTotal()).isZero();
    }

    @Test
    void lesAttributsDeVarianteSontStockesEnJson() {
        Produit produit = FabriqueCatalogue.produit(em);
        String sku = unique("A15-GRIS");
        produit.ajouterVariante(sku, "Gris", 450_000, 1)
                .setAttributs(Map.of("couleur", "Gris éclipse", "ram", "16 Go"));
        produitDepot.saveAndFlush(produit);
        em.clear();

        assertThat(varianteDepot.findBySku(sku)).get()
                .extracting(VarianteProduit::getAttributs)
                .isEqualTo(Map.of("couleur", "Gris éclipse", "ram", "16 Go"));
    }

    @Test
    void unProduitNAQuUneSeuleImagePrincipale() {
        Produit produit = FabriqueCatalogue.produit(em);
        MediaProduit premiere = produit.ajouterMedia(TypeMedia.IMAGE, "https://exemple/1.webp", "Vue de face");
        MediaProduit seconde = produit.ajouterMedia(TypeMedia.IMAGE, "https://exemple/2.webp", "Vue de profil");
        assertThat(premiere.isPrincipal()).isTrue();
        seconde.setPrincipal(true);

        assertThatThrownBy(() -> produitDepot.saveAndFlush(produit))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("uk_media_produit_principal"));
    }

    @Test
    void uneCaracteristiqueNAQuUneValeurParProduit() {
        Produit produit = FabriqueCatalogue.produit(em);
        DefinitionCaracteristique ram = new DefinitionCaracteristique(
                produit.getCategorie(), "ram_go", "Mémoire vive", TypeCaracteristique.NOMBRE, "Mémoire");
        em.persist(ram);
        produit.ajouterCaracteristique(ram).setValeurNombre(new BigDecimal("16"));
        produit.ajouterCaracteristique(ram).setValeurNombre(new BigDecimal("32"));

        // Même sans variante (NULL), le doublon est refusé : UNIQUE NULLS NOT DISTINCT
        assertThatThrownBy(() -> produitDepot.saveAndFlush(produit))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("uk_valeur_caracteristique"));
    }

    @Test
    void uneValeurDeCaracteristiqueDoitEtreRenseignee() {
        Produit produit = FabriqueCatalogue.produit(em);
        DefinitionCaracteristique socket = new DefinitionCaracteristique(
                produit.getCategorie(), "socket", "Socket", TypeCaracteristique.TEXTE, "Processeur");
        em.persist(socket);
        produit.ajouterCaracteristique(socket);

        assertThatThrownBy(() -> produitDepot.saveAndFlush(produit))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_valeur_renseignee"));
    }

    @Test
    void unAncienSlugPermetDeRetrouverLEntite() {
        String ancien = unique("asus-tuf-a15-2023");
        historiqueSlugDepot.saveAndFlush(new HistoriqueSlug(TypeEntiteSlug.PRODUIT, ancien, 42L));

        assertThat(historiqueSlugDepot.findByTypeEntiteAndAncienSlug(TypeEntiteSlug.PRODUIT, ancien))
                .get()
                .extracting(HistoriqueSlug::getEntiteId)
                .isEqualTo(42L);
        assertThat(historiqueSlugDepot.findByTypeEntiteAndAncienSlug(TypeEntiteSlug.CATEGORIE, ancien))
                .isEmpty();
    }

    /** Nom de la contrainte PostgreSQL violée, quelle que soit l'exception Spring qui l'enveloppe. */
    static String contrainteViolee(Throwable erreur) {
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
