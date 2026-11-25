package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.DefinitionCaracteristique;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeCaracteristique;
import sn.boutique.catalogue.modele.TypeMedia;
import sn.boutique.catalogue.modele.VarianteProduit;
import tools.jackson.databind.JsonNode;

/** Fiche produit et produits similaires. */
class FicheProduitTest extends CatalogueTestBase {

    @Test
    void laFicheContientVariantesMediasTriesEtCaracteristiquesGroupees() throws Exception {
        Categorie racine = categorie("Ordinateurs", null, 0, true);
        Categorie portables = categorie("PC portables", racine, 0, true);
        DefinitionCaracteristique processeur = definition(portables, "processeur_modele", "Processeur",
                TypeCaracteristique.TEXTE, "Processeur", 1, null, false);
        DefinitionCaracteristique ram = definition(portables, "ram_go", "Mémoire vive",
                TypeCaracteristique.NOMBRE, "Mémoire", 2, "Go", true);
        DefinitionCaracteristique ecran = definition(portables, "ecran_pouces", "Taille d'écran",
                TypeCaracteristique.NOMBRE, "Écran", 3, "pouces", true);
        DefinitionCaracteristique azerty = definition(portables, "clavier_azerty", "Clavier AZERTY",
                TypeCaracteristique.BOOLEEN, "Clavier", 4, null, true);
        String sku8 = unique("VIVO-8"), sku16 = unique("VIVO-16"), skuArrete = unique("VIVO-32");

        Produit produit = produit("Vivobook 15", marque("Asus"), portables, p -> {
            p.ajouterVariante(sku8, "8 Go / 512 Go", 389_000, 7).setAttributs(Map.of("ram", "8 Go"));
            VarianteProduit v16 = p.ajouterVariante(sku16, "16 Go / 512 Go", 449_000, 2);
            v16.setPrixBarre(479_000L);
            v16.setAttributs(Map.of("ram", "16 Go"));
            p.ajouterVariante(skuArrete, "32 Go / 1 To", 599_000, 3).setActif(false);
            p.ajouterMedia(TypeMedia.IMAGE, "https://exemple.sn/face.jpg", "Vivobook 15 vu de face");
            p.ajouterMedia(TypeMedia.IMAGE, "https://exemple.sn/profil.jpg", "Vivobook 15 de profil");
            p.ajouterMedia(TypeMedia.VIDEO_YOUTUBE, "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                    "Présentation du Vivobook 15").setUrlMiniature("https://exemple.sn/miniature.jpg");
            p.ajouterMedia(TypeMedia.IMAGE, "https://exemple.sn/32go.jpg", "Version 32 Go")
                    .setVariante(p.getVariantes().get(2));
        });
        caracteristiques(produit, (em, p) -> {
            valeur(em, p, processeur, null, "Intel Core i5-1335U");
            valeur(em, p, ecran, null, 15.6);
            valeur(em, p, azerty, null, true);
            valeur(em, p, ram, sku8, 8);
            valeur(em, p, ram, sku16, 16);
            valeur(em, p, ram, skuArrete, 32);
        });

        RequeteComptee requete = lireEnComptant("/api/produits/{slug}", produit.getSlug());

        requete.resultat().andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Vivobook 15"))
                .andExpect(jsonPath("$.marque.nom").value("Asus"))
                .andExpect(jsonPath("$.prixMinimum").value(389_000))
                .andExpect(jsonPath("$.disponibilite").value("EN_STOCK"))
                // Variantes actives, de la moins chère à la plus chère ; pas de stock exact exposé
                .andExpect(jsonPath("$.variantes.length()").value(2))
                .andExpect(jsonPath("$.variantes[0].sku").value(sku8))
                .andExpect(jsonPath("$.variantes[0].reduction").doesNotExist())
                .andExpect(jsonPath("$.variantes[1].sku").value(sku16))
                .andExpect(jsonPath("$.variantes[1].prixBarre").value(479_000))
                .andExpect(jsonPath("$.variantes[1].reduction").value(6))
                .andExpect(jsonPath("$.variantes[1].disponibilite").value("STOCK_FAIBLE"))
                .andExpect(jsonPath("$.variantes[1].stock").doesNotExist())
                .andExpect(jsonPath("$.variantes[1].caracteristiques[0].cle").value("ram_go"))
                .andExpect(jsonPath("$.variantes[1].caracteristiques[0].valeur").value("16"))
                .andExpect(jsonPath("$.variantes[1].caracteristiques[0].unite").value("Go"))
                .andExpect(jsonPath("$.filAriane[0].slug").value(racine.getSlug()))
                .andExpect(jsonPath("$.filAriane[1].slug").value(portables.getSlug()));

        JsonNode fiche = corps(requete.resultat());
        // Médias dans l'ordre de la galerie ; la photo de la variante désactivée n'apparaît pas
        assertThat(fiche.get("medias").findValuesAsString("texteAlternatif")).containsExactly(
                "Vivobook 15 vu de face", "Vivobook 15 de profil", "Présentation du Vivobook 15");
        assertThat(fiche.get("medias").get(0).get("principal").asBoolean()).isTrue();
        assertThat(fiche.get("medias").get(2).get("idYoutube").asString()).isEqualTo("dQw4w9WgXcQ");
        // Groupes dans l'ordre des définitions, valeurs en français, unité à part
        assertThat(fiche.get("caracteristiques").findValuesAsString("groupe"))
                .containsExactly("Processeur", "Mémoire", "Écran", "Clavier");
        JsonNode memoire = fiche.get("caracteristiques").get(1).get("elements").get(0);
        assertThat(memoire.get("valeur").asString()).isEqualTo("8");
        assertThat(memoire.get("parVariante").asBoolean()).isTrue();
        JsonNode taille = fiche.get("caracteristiques").get(2).get("elements").get(0);
        assertThat(List.of(taille.get("valeur").asString(), taille.get("unite").asString()))
                .containsExactly("15,6", "pouces");
        assertThat(fiche.get("caracteristiques").get(3).get("elements").get(0).get("valeur").asString())
                .isEqualTo("Oui");

        assertThat(requete.requetesSql()).as("requêtes SQL de la fiche").isLessThanOrEqualTo(5);
    }

    @Test
    void unProduitInactifOuInconnuRenvoie404() throws Exception {
        Produit inactif = produit("Ancien modèle", marque("HP"), categorie("PC", null, 0, true), false);

        for (String slug : List.of(inactif.getSlug(), "produit-inexistant")) {
            lire("/api/produits/{slug}", slug)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESSOURCE_INTROUVABLE"));
            lire("/api/produits/{slug}/similaires", slug).andExpect(status().isNotFound());
        }
    }

    @Test
    void unProduitDontToutesLesVariantesSontEnRuptureEstEnRupture() throws Exception {
        Produit produit = produit("Clé USB 64 Go", marque("SanDisk"), categorie("Stockage", null, 0, true), p -> {
            p.ajouterVariante(unique("USB-A"), "USB-A", 6_500, 0);
            p.ajouterVariante(unique("USB-C"), "USB-C", 7_500, 0);
            p.ajouterMedia(TypeMedia.IMAGE, "https://exemple.sn/cle.jpg", "Clé USB");
        });

        lire("/api/produits/{slug}", produit.getSlug())
                .andExpect(jsonPath("$.disponibilite").value("RUPTURE"))
                .andExpect(jsonPath("$.variantes[0].disponibilite").value("RUPTURE"));
    }

    @Test
    void lesSimilairesSontDeLaMemeCategorieEnStockDAbordPuisAuPrixLePlusProche() throws Exception {
        Categorie ecrans = categorie("Écrans", null, 0, true);
        Categorie autre = categorie("Souris", null, 0, true);
        Marque marque = marque("Samsung");
        Produit reference = produitSimple("Odyssey G5 27", marque, ecrans, 200_000, 5);
        Produit proche = produitSimple("Odyssey G4 27", marque, ecrans, 190_000, 5);
        Produit loin = produitSimple("Odyssey G9 49", marque, ecrans, 900_000, 5);
        Produit rupture = produitSimple("Odyssey G5 32", marque, ecrans, 201_000, 0);
        Produit inactif = enTransaction(em -> {
            Produit p = em.find(Produit.class, produitSimple("Ancien écran", marque, ecrans, 199_000, 5).getId());
            p.setActif(false);
            return p;
        });
        Produit autreCategorie = produitSimple("Souris sans fil", marque, autre, 200_000, 5);
        // Deux variantes : la carte montre la moins chère
        Produit deuxVariantes = produit("ViewFinity S6", marque, ecrans, p -> {
            p.ajouterVariante(unique("S6-27"), "27 pouces", 250_000, 4);
            p.ajouterVariante(unique("S6-32"), "32 pouces", 320_000, 4);
            p.ajouterMedia(TypeMedia.IMAGE, "https://exemple.sn/s6.jpg", "ViewFinity S6");
        });

        RequeteComptee requete = lireEnComptant("/api/produits/{slug}/similaires", reference.getSlug());

        JsonNode similaires = corps(requete.resultat());
        assertThat(slugs(similaires)).containsExactly(
                proche.getSlug(), deuxVariantes.getSlug(), loin.getSlug(), rupture.getSlug());
        assertThat(slugs(similaires))
                .doesNotContain(reference.getSlug(), inactif.getSlug(), autreCategorie.getSlug());
        JsonNode carte = similaires.get(1);
        assertThat(carte.get("prix").asLong()).isEqualTo(250_000);
        assertThat(carte.get("nombreVariantes").asInt()).isEqualTo(2);
        assertThat(carte.get("varianteId").asLong()).isPositive();
        assertThat(carte.get("image").get("url").asString()).isEqualTo("https://exemple.sn/s6.jpg");
        assertThat(similaires.get(3).get("disponibilite").asString()).isEqualTo("RUPTURE");
        assertThat(requete.requetesSql()).isLessThanOrEqualTo(3);
    }

    private static List<String> slugs(JsonNode cartes) {
        return cartes.valueStream().map(carte -> carte.get("slug").asString()).toList();
    }

    private Produit produitSimple(String nom, Marque marque, Categorie categorie, long prix, int stock) {
        return produit(nom, marque, categorie, p -> {
            p.ajouterVariante(unique("SKU"), "Standard", prix, stock);
            p.ajouterMedia(TypeMedia.IMAGE, "https://exemple.sn/" + p.getSlug() + ".jpg", nom);
        });
    }
}
