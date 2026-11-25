package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.HistoriqueSlug;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeEntiteSlug;
import sn.boutique.marketing.modele.Banniere;
import sn.boutique.marketing.modele.EmplacementBanniere;
import tools.jackson.databind.JsonNode;

/** Catégories, marques, paramètres, bannières et redirections : contenu, visibilité et nombre de requêtes. */
class LecturesCatalogueTest extends CatalogueTestBase {

    // --- Catégories -------------------------------------------------------------------------------

    @Test
    void lArbreCompteLesProduitsActifsDesSousCategoriesEtMasqueLesCategoriesInactives() throws Exception {
        Categorie racine = categorie("Ordinateurs", null, 0, true);
        Categorie portables = categorie("PC portables", racine, 1, true);
        Categorie bureau = categorie("PC de bureau", racine, 0, true);
        Categorie archivee = categorie("Netbooks", racine, 2, false);
        Categorie sousArchivee = categorie("Netbooks 10 pouces", archivee, 0, true);
        Marque marque = marque("Asus");
        produit("Vivobook 15", marque, portables, true);
        produit("TUF A15", marque, portables, true);
        produit("Ancien modèle", marque, portables, false);
        produit("ExpertCenter", marque, bureau, true);
        produit("Eee PC", marque, sousArchivee, true);

        RequeteComptee requete = lireEnComptant("/api/categories");

        requete.resultat().andExpect(status().isOk());
        assertThat(requete.requetesSql()).isEqualTo(1);
        JsonNode noeudRacine = noeud(corps(requete.resultat()), racine.getSlug());
        assertThat(noeudRacine.get("nombreProduits").asLong()).isEqualTo(3);
        // Ordre d'affichage (position) et sous-catégorie inactive absente, avec sa propre sous-catégorie
        assertThat(noeudRacine.get("enfants").findValuesAsString("slug"))
                .containsExactly(bureau.getSlug(), portables.getSlug());
        assertThat(noeud(noeudRacine.get("enfants"), portables.getSlug()).get("nombreProduits").asLong()).isEqualTo(2);
        assertThat(noeud(corps(requete.resultat()), sousArchivee.getSlug())).isNull();
    }

    @Test
    void uneCategorieDonneSonFilArianeSesEnfantsEtSesTextes() throws Exception {
        Categorie racine = categorie("Composants", null, 0, true);
        Categorie memoire = categorie("Mémoire RAM", racine, 0, true);
        Categorie ddr5 = categorie("DDR5", memoire, 0, true);
        enTransaction(em -> {
            Categorie c = em.find(Categorie.class, memoire.getId());
            c.setDescription("Barrettes pour PC portables et de bureau.");
            c.setTexteSeo("<p>Bien choisir sa mémoire</p>");
            c.setMetaTitre("Mémoire RAM DDR4 et DDR5");
            return c;
        });
        produit("Kingston Fury 16 Go", marque("Kingston"), ddr5, true);

        RequeteComptee requete = lireEnComptant("/api/categories/{slug}", memoire.getSlug());

        requete.resultat().andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Mémoire RAM"))
                .andExpect(jsonPath("$.metaTitre").value("Mémoire RAM DDR4 et DDR5"))
                .andExpect(jsonPath("$.texteSeo").value("<p>Bien choisir sa mémoire</p>"))
                .andExpect(jsonPath("$.nombreProduits").value(1))
                .andExpect(jsonPath("$.filAriane.length()").value(1))
                .andExpect(jsonPath("$.filAriane[0].slug").value(racine.getSlug()))
                .andExpect(jsonPath("$.enfants[0].slug").value(ddr5.getSlug()))
                .andExpect(jsonPath("$.enfants[0].nombreProduits").value(1));
        assertThat(requete.requetesSql()).isLessThanOrEqualTo(2);
    }

    @Test
    void uneCategorieInactiveOuSousUnParentInactifRenvoie404() throws Exception {
        Categorie inactive = categorie("Archives", null, 0, false);
        Categorie enfant = categorie("Sous-archives", inactive, 0, true);

        for (String slug : List.of(inactive.getSlug(), enfant.getSlug(), "categorie-inexistante")) {
            lire("/api/categories/{slug}", slug)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESSOURCE_INTROUVABLE"));
        }
    }

    // --- Marques ------------------------------------------------------------------------------------

    @Test
    void seulesLesMarquesAyantUnProduitActifSontListees() throws Exception {
        Categorie categorie = categorie("Écrans", null, 0, true);
        Marque samsung = marque("Samsung");
        Marque sansProduitActif = marque("Marque retirée");
        produit("Odyssey G5", samsung, categorie, true);
        produit("Odyssey G7", samsung, categorie, true);
        produit("Modèle arrêté", sansProduitActif, categorie, false);

        RequeteComptee requete = lireEnComptant("/api/marques");

        assertThat(requete.requetesSql()).isEqualTo(1);
        JsonNode marques = corps(requete.resultat());
        assertThat(noeud(marques, samsung.getSlug()).get("nombreProduits").asLong()).isEqualTo(2);
        assertThat(noeud(marques, sansProduitActif.getSlug())).isNull();

        RequeteComptee detail = lireEnComptant("/api/marques/{slug}", samsung.getSlug());
        detail.resultat().andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Samsung"))
                .andExpect(jsonPath("$.nombreProduits").value(2));
        assertThat(detail.requetesSql()).isEqualTo(1);
        lire("/api/marques/{slug}", "marque-inexistante").andExpect(status().isNotFound());
    }

    // --- Paramètres ---------------------------------------------------------------------------------

    @Test
    void lesParametresPublicsSontLisiblesEnUneRequete() throws Exception {
        RequeteComptee requete = lireEnComptant("/api/parametres");

        requete.resultat().andExpect(status().isOk())
                .andExpect(jsonPath("$.nomSite").isNotEmpty())
                .andExpect(jsonPath("$.suivi").isMap())
                .andExpect(jsonPath("$.verification").isMap())
                .andExpect(jsonPath("$.liensReseauxSociaux").isMap())
                .andExpect(jsonPath("$.avisReservesAuxAcheteurs").value(true))
                // Les identifiants internes de stockage des images ne sont pas exposés
                .andExpect(jsonPath("$.logoIdPublic").doesNotExist());
        assertThat(requete.requetesSql()).isEqualTo(1);
    }

    // --- Bannières ----------------------------------------------------------------------------------

    @Test
    void seulesLesBannieresActivesEtDansLeurPeriodeSontAffichees() throws Exception {
        Instant maintenant = horloge.instant();
        Banniere permanente = banniere(EmplacementBanniere.ACCUEIL_CARROUSEL, null, null, null, true, 2);
        Banniere enCours = banniere(EmplacementBanniere.ACCUEIL_CARROUSEL, null,
                maintenant.minus(Duration.ofDays(1)), maintenant.plus(Duration.ofDays(2)), true, 1);
        Banniere future = banniere(EmplacementBanniere.ACCUEIL_CARROUSEL, null,
                maintenant.plus(Duration.ofHours(3)), null, true, 0);
        Banniere terminee = banniere(EmplacementBanniere.ACCUEIL_CARROUSEL, null,
                null, maintenant.minus(Duration.ofMinutes(1)), true, 0);
        Banniere inactive = banniere(EmplacementBanniere.ACCUEIL_CARROUSEL, null, null, null, false, 0);

        RequeteComptee requete = lireEnComptant("/api/bannieres?emplacement=ACCUEIL_CARROUSEL");

        List<Long> ids = ids(corps(requete.resultat()));
        assertThat(ids).contains(enCours.getId(), permanente.getId())
                .doesNotContain(future.getId(), terminee.getId(), inactive.getId());
        assertThat(ids.indexOf(enCours.getId())).isLessThan(ids.indexOf(permanente.getId()));
        assertThat(requete.requetesSql()).isEqualTo(1);
        requete.resultat().andExpect(jsonPath("$[?(@.id == %d)].dateFin".formatted(enCours.getId())).isNotEmpty());

        // Trois heures plus tard, la bannière programmée apparaît
        horloge.avancer(Duration.ofHours(3));
        assertThat(ids(corps(lire("/api/bannieres?emplacement=ACCUEIL_CARROUSEL")))).contains(future.getId());
    }

    @Test
    void lEnteteDeCategorieFiltreParCategorieEtExigeLeParametre() throws Exception {
        Categorie ecrans = categorie("Écrans", null, 0, true);
        Categorie reseau = categorie("Réseau", null, 0, true);
        Banniere banniereEcrans = banniere(EmplacementBanniere.ENTETE_CATEGORIE, ecrans, null, null, true, 0);
        Banniere banniereReseau = banniere(EmplacementBanniere.ENTETE_CATEGORIE, reseau, null, null, true, 0);

        assertThat(ids(corps(lire("/api/bannieres?emplacement=ENTETE_CATEGORIE&categorie={slug}", ecrans.getSlug()))))
                .containsExactly(banniereEcrans.getId())
                .doesNotContain(banniereReseau.getId());
        lire("/api/bannieres?emplacement=ENTETE_CATEGORIE").andExpect(status().isBadRequest());
        lire("/api/bannieres?emplacement=EMPLACEMENT_INCONNU").andExpect(status().isBadRequest());
        lire("/api/bannieres").andExpect(status().isBadRequest());
    }

    // --- Redirections -------------------------------------------------------------------------------

    @Test
    void unAncienSlugRedirigeEn301VersLeCheminActuel() throws Exception {
        Categorie categorie = categorie("PC portables", null, 0, true);
        Marque marque = marque("Lenovo");
        Produit produit = produit("IdeaPad Slim 3", marque, categorie, true);
        Produit desactive = produit("IdeaPad 1", marque, categorie, false);
        String ancienProduit = unique("ideapad-slim-3-ancien");
        String ancienDesactive = unique("ideapad-1-ancien");
        String ancienneCategorie = unique("portables-ancien");
        String ancienneMarque = unique("lenovo-ancien");
        enTransaction(em -> {
            em.persist(new HistoriqueSlug(TypeEntiteSlug.PRODUIT, ancienProduit, produit.getId()));
            em.persist(new HistoriqueSlug(TypeEntiteSlug.PRODUIT, ancienDesactive, desactive.getId()));
            em.persist(new HistoriqueSlug(TypeEntiteSlug.CATEGORIE, ancienneCategorie, categorie.getId()));
            em.persist(new HistoriqueSlug(TypeEntiteSlug.MARQUE, ancienneMarque, marque.getId()));
            return null;
        });

        lire("/api/redirections?chemin=/produit/{slug}", ancienProduit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("/produit/" + produit.getSlug()))
                .andExpect(jsonPath("$.statut").value(301));
        lire("/api/redirections?chemin=/categorie/{slug}/", ancienneCategorie)
                .andExpect(jsonPath("$.destination").value("/categorie/" + categorie.getSlug()));
        lire("/api/redirections?chemin=/marque/{slug}", ancienneMarque)
                .andExpect(jsonPath("$.destination").value("/marque/" + marque.getSlug()));

        // Produit désactivé (301 ou 410 : phase 3), slug inconnu, mauvais type, chemin invalide : 404
        for (String chemin : List.of("/produit/" + ancienDesactive, "/produit/slug-jamais-vu",
                "/marque/" + ancienProduit, "/produit/" + produit.getSlug(), "/admin/../produit/" + ancienProduit,
                "https://site-pirate.com/produit/" + ancienProduit)) {
            lire("/api/redirections?chemin={chemin}", chemin).andExpect(status().isNotFound());
        }
        lire("/api/redirections").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------------------------------------

    private Banniere banniere(EmplacementBanniere emplacement, Categorie categorie, Instant debut, Instant fin,
                              boolean actif, int position) {
        return enTransaction(em -> {
            Banniere banniere = new Banniere(emplacement, "https://exemple.sn/o.jpg", "https://exemple.sn/m.jpg",
                    "Promotion sur les PC portables");
            banniere.setCategorie(categorie == null ? null : em.getReference(Categorie.class, categorie.getId()));
            banniere.setDateDebut(debut);
            banniere.setDateFin(fin);
            banniere.setActif(actif);
            banniere.setPosition(position);
            em.persist(banniere);
            return banniere;
        });
    }

    private static List<Long> ids(JsonNode liste) {
        List<Long> ids = new ArrayList<>();
        liste.forEach(element -> ids.add(element.get("id").asLong()));
        return ids;
    }
}
