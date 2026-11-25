package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeMedia;
import tools.jackson.databind.JsonNode;

/**
 * Recherche plein texte, bascule sur les trigrammes et autocomplétion. Les noms sont inventés pour ne
 * pas croiser les produits des autres classes (base partagée entre les classes de test).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RechercheTest extends CatalogueTestBase {

    private Categorie ultraportables;
    private Categorie moniteurs;
    private Marque zorvex;
    private Marque kaltron;
    private Produit zorvexAir;
    private Produit zorvexPro;
    private Produit kaltronBook;
    private Produit ecran;

    @BeforeAll
    void creerLeCatalogue() {
        Categorie racine = categorie("Informatique nomade", null, 0, true);
        ultraportables = categorie("Ultraportables", racine, 0, true);
        moniteurs = categorie("Moniteurs", null, 1, true);
        zorvex = marque("Zorvex");
        kaltron = marque("Kaltron");
        zorvexAir = produit("Zorvex Quillbook Air", zorvex, ultraportables, p -> {
            p.ajouterVariante(unique("ZQA"), "8 Go", 420_000, 3);
            image(p);
        });
        zorvexPro = produit("Zorvex Quillbook Pro", zorvex, ultraportables, p -> {
            p.ajouterVariante(unique("ZQP"), "16 Go", 610_000, 2);
            image(p);
        });
        kaltronBook = produit("Kaltron Quillbook 14", kaltron, ultraportables, p -> {
            p.ajouterVariante(unique("KQ14"), "8 Go", 380_000, 0);
            image(p);
        });
        ecran = produit("Écran Quillvue Incurvé 27", zorvex, moniteurs, p -> {
            p.ajouterVariante(unique("EQV"), "Standard", 210_000, 5);
            image(p);
        });
        produit("Quillbook retiré", kaltron, ultraportables, p -> {
            p.setActif(false);
            p.ajouterVariante(unique("QRT"), "Standard", 100_000, 5);
            image(p);
        });
    }

    @Test
    void laRechercheTrouveLesProduitsSurLeNomEtDonneLesFacettes() throws Exception {
        RequeteComptee requete = lireEnComptant("/api/recherche?q={q}", "quillbook");

        JsonNode resultat = corps(requete.resultat());
        assertThat(resultat.get("approximatif").asBoolean()).isFalse();
        assertThat(resultat.get("requete").asString()).isEqualTo("quillbook");
        assertThat(slugs(resultat)).containsExactlyInAnyOrder(zorvexAir.getSlug(), zorvexPro.getSlug(),
                kaltronBook.getSlug());
        assertThat(resultat.get("totalElements").asLong()).isEqualTo(3);
        assertThat(options(resultat, "marques")).containsExactly(zorvex.getSlug() + ":2:false",
                kaltron.getSlug() + ":1:false");
        assertThat(options(resultat, "categories")).containsExactly(ultraportables.getSlug() + ":3:false");
        assertThat(resultat.get("suggestions").isNull()).isTrue();
        assertThat(requete.requetesSql()).as("requêtes SQL de la recherche").isLessThanOrEqualTo(6);
    }

    @Test
    void laRechercheIgnoreLesAccentsEtLaCasse() throws Exception {
        assertThat(slugs(corps(lire("/api/recherche?q={q}", "ECRAN incurve")))).containsExactly(ecran.getSlug());
        // La marque fait partie du vecteur : « zorvex » trouve aussi l'écran
        assertThat(slugs(corps(lire("/api/recherche?q={q}", "zorvex")))).containsExactlyInAnyOrder(
                zorvexAir.getSlug(), zorvexPro.getSlug(), ecran.getSlug());
    }

    @Test
    void lesFiltresSAppliquentEtLaFacetteMarqueIgnoreSonPropreFiltre() throws Exception {
        JsonNode resultat = corps(lire("/api/recherche?q={q}&marque={m}&tri=prix-croissant", "quillbook",
                zorvex.getSlug()));

        assertThat(slugs(resultat)).containsExactly(zorvexAir.getSlug(), zorvexPro.getSlug());
        assertThat(options(resultat, "marques")).containsExactly(zorvex.getSlug() + ":2:true",
                kaltron.getSlug() + ":1:false");

        JsonNode disponibles = corps(lire("/api/recherche?q={q}&disponible=true&categorie={c}", "quillbook",
                ultraportables.getSlug()));
        assertThat(slugs(disponibles)).containsExactlyInAnyOrder(zorvexAir.getSlug(), zorvexPro.getSlug());
        // La facette des catégories ignore le filtre de catégorie
        JsonNode categorieFiltree = corps(lire("/api/recherche?q={q}&categorie={c}", "zorvex", moniteurs.getSlug()));
        assertThat(slugs(categorieFiltree)).containsExactly(ecran.getSlug());
        assertThat(options(categorieFiltree, "categories")).containsExactlyInAnyOrder(
                ultraportables.getSlug() + ":2:false", moniteurs.getSlug() + ":1:false");
    }

    @Test
    void uneFauteDeFrappeBasculeSurUneRechercheApprochante() throws Exception {
        JsonNode resultat = corps(lire("/api/recherche?q={q}", "kaltrn"));

        assertThat(resultat.get("approximatif").asBoolean()).isTrue();
        assertThat(slugs(resultat)).containsExactly(kaltronBook.getSlug());
        assertThat(resultat.get("suggestions").isNull()).isTrue();

        // Lettre manquante au milieu du mot (similarité 0,5, sous le seuil par défaut de pg_trgm)
        JsonNode lettreManquante = corps(lire("/api/recherche?q={q}", "kalron"));
        assertThat(lettreManquante.get("approximatif").asBoolean()).isTrue();
        assertThat(slugs(lettreManquante)).containsExactly(kaltronBook.getSlug());
    }

    @Test
    void sansAucunResultatLaReponseProposeDesSuggestions() throws Exception {
        JsonNode resultat = corps(lire("/api/recherche?q={q}", "xyzwvq"));

        assertThat(resultat.get("totalElements").asLong()).isZero();
        assertThat(resultat.get("approximatif").asBoolean()).isFalse();
        JsonNode suggestions = resultat.get("suggestions");
        assertThat(suggestions.get("categories").valueStream().map(c -> c.get("slug").asString()).toList())
                .contains(moniteurs.getSlug());
        assertThat(suggestions.get("produits").size()).isBetween(1, 8);
        // Uniquement des produits en stock
        assertThat(suggestions.get("produits").valueStream().map(p -> p.get("disponibilite").asString()))
                .doesNotContain("RUPTURE");
    }

    @Test
    void lAutocompletionProposeProduitsCategoriesEtMarques() throws Exception {
        RequeteComptee requete = lireEnComptant("/api/recherche/suggestions?q={q}", "zorv");

        JsonNode suggestions = corps(requete.resultat());
        assertThat(suggestions.get("produits").valueStream().map(p -> p.get("slug").asString()).toList())
                .containsExactlyInAnyOrder(zorvexAir.getSlug(), zorvexPro.getSlug(), ecran.getSlug());
        assertThat(suggestions.get("marques").valueStream().map(m -> m.get("slug").asString()).toList())
                .containsExactly(zorvex.getSlug());
        assertThat(requete.requetesSql()).as("requêtes SQL de l'autocomplétion").isLessThanOrEqualTo(5);

        JsonNode categories = corps(lire("/api/recherche/suggestions?q={q}", "ultrapo"));
        assertThat(categories.get("categories").valueStream().map(c -> c.get("slug").asString()).toList())
                .containsExactly(ultraportables.getSlug());
        // Début de mot à l'intérieur du nom, insensible aux accents
        assertThat(corps(lire("/api/recherche/suggestions?q={q}", "quillvué")).get("produits").valueStream()
                .map(p -> p.get("slug").asString()).toList()).containsExactly(ecran.getSlug());
    }

    @ParameterizedTest
    @ValueSource(strings = {"c++", "(.*)", "a|b", "[x", "\\d+", "50%", "o'neil", "\"quill"})
    void lesCaracteresSpeciauxNeProvoquentPasDErreur(String saisie) throws Exception {
        lire("/api/recherche?q={q}", saisie).andExpect(status().isOk());
        lire("/api/recherche/suggestions?q={q}", saisie).andExpect(status().isOk());
    }

    @Test
    void laLongueurDeLaRequeteEstControlee() throws Exception {
        for (String url : List.of("/api/recherche?q=a", "/api/recherche", 
                "/api/recherche?q=" + "x".repeat(101))) {
            lire(url).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"))
                    .andExpect(jsonPath("$.parametre").value("q"));
        }
        lire("/api/recherche/suggestions?q={q}", " b ").andExpect(status().isBadRequest());
        lire("/api/recherche?q={q}&tri=pertinence", "quillbook").andExpect(status().isOk());
    }

    private static void image(Produit produit) {
        produit.ajouterMedia(TypeMedia.IMAGE, "https://exemple.sn/" + produit.getSlug() + ".jpg", produit.getNom());
    }

    private static List<String> slugs(JsonNode resultat) {
        return resultat.get("elements").valueStream().map(carte -> carte.get("slug").asString()).toList();
    }

    /** Facette « categories » ou « marques » : « slug:nombre:sélectionnée ». */
    private static List<String> options(JsonNode resultat, String facette) {
        return resultat.get(facette).valueStream()
                .map(o -> "%s:%d:%s".formatted(o.get("valeur").asString(), o.get("nombre").asLong(),
                        o.get("selectionne").asBoolean()))
                .toList();
    }
}
