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
import sn.boutique.catalogue.modele.DefinitionCaracteristique;
import sn.boutique.catalogue.modele.EtatProduit;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeCaracteristique;
import sn.boutique.catalogue.modele.TypeMedia;
import tools.jackson.databind.JsonNode;

/**
 * Listes filtrées et facettes. Jeu de données propre à la classe :
 * <pre>
 * Ordinateurs
 * ├── PC portables  (ram_go*, ecran_pouces, systeme, type_stockage)
 * │   ├── Vivobook (Asus, neuf)        8 Go 389 000 (stock 7) | 16 Go 449 000 (stock 2)   RAM par variante
 * │   ├── IdeaPad (Lenovo, neuf)       8 Go 350 000 (stock 5) | 16 Go 420 000 (rupture)   RAM par variante
 * │   ├── Latitude (Dell, reconditionné)  32 Go 300 000 (stock 3)   RAM au niveau du produit
 * │   └── Ancien (Asus, inactif)       16 Go 100 000
 * ├── PC gaming     (ram_go, systeme, frequence_hz)
 * │   └── TUF (Asus, neuf, mis en avant)  16 Go 650 000 (stock 4)
 * └── PC de bureau  (ram_go, systeme)
 *     └── ExpertCenter (Asus, neuf, 10 ventes)  16 Go 500 000 (stock 1)
 * </pre>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListeProduitsTest extends CatalogueTestBase {

    private Categorie ordinateurs;
    private Categorie portables;
    private Marque asus;
    private Marque lenovo;
    private Produit vivobook;
    private Produit ideapad;
    private Produit latitude;
    private Produit tuf;
    private Produit expertCenter;
    private String vivobook8;
    private String vivobook16;
    private String ideapad16;

    @BeforeAll
    void creerLeCatalogue() {
        ordinateurs = categorie("Ordinateurs", null, 0, true);
        portables = categorie("PC portables", ordinateurs, 0, true);
        Categorie gaming = categorie("PC gaming", ordinateurs, 1, true);
        Categorie bureau = categorie("PC de bureau", ordinateurs, 2, true);
        DefinitionCaracteristique ramPortables = definition(portables, "ram_go", "Mémoire vive",
                TypeCaracteristique.NOMBRE, "Mémoire", 1, "Go", true);
        DefinitionCaracteristique ecran = definition(portables, "ecran_pouces", "Écran",
                TypeCaracteristique.NOMBRE, "Écran", 2, "pouces", true);
        DefinitionCaracteristique systemePortables = definition(portables, "systeme", "Système",
                TypeCaracteristique.LISTE, "Logiciel", 3, null, true);
        DefinitionCaracteristique stockage = definition(portables, "type_stockage", "Type de stockage",
                TypeCaracteristique.LISTE, "Stockage", 4, null, true);
        DefinitionCaracteristique ramGaming = definition(gaming, "ram_go", "Mémoire vive",
                TypeCaracteristique.NOMBRE, "Mémoire", 1, "Go", true);
        DefinitionCaracteristique systemeGaming = definition(gaming, "systeme", "Système",
                TypeCaracteristique.LISTE, "Logiciel", 2, null, true);
        definition(gaming, "frequence_hz", "Fréquence", TypeCaracteristique.NOMBRE, "Écran", 3, "Hz", true);
        DefinitionCaracteristique ramBureau = definition(bureau, "ram_go", "Mémoire vive",
                TypeCaracteristique.NOMBRE, "Mémoire", 1, "Go", true);
        DefinitionCaracteristique systemeBureau = definition(bureau, "systeme", "Système",
                TypeCaracteristique.LISTE, "Logiciel", 2, null, true);
        asus = marque("Asus");
        lenovo = marque("Lenovo");
        Marque dell = marque("Dell");
        vivobook8 = unique("VIVO-8");
        vivobook16 = unique("VIVO-16");
        String ideapad8 = unique("IDEA-8");
        ideapad16 = unique("IDEA-16");
        String latitude32 = unique("LAT-32");
        String tuf16 = unique("TUF-16");
        String expert16 = unique("EXP-16");
        String ancien16 = unique("ANC-16");

        vivobook = produit("Vivobook 15", asus, portables, p -> {
            p.ajouterVariante(vivobook8, "8 Go", 389_000, 7);
            p.ajouterVariante(vivobook16, "16 Go", 449_000, 2).setPrixBarre(479_000L);
            image(p);
        });
        caracteristiques(vivobook, (em, p) -> {
            valeur(em, p, ramPortables, vivobook8, 8);
            valeur(em, p, ramPortables, vivobook16, 16);
            valeur(em, p, ecran, null, 15.6);
            valeur(em, p, systemePortables, null, "Windows 11 Famille");
            valeur(em, p, stockage, null, "SSD NVMe");
        });
        ideapad = produit("IdeaPad Slim 3", lenovo, portables, p -> {
            p.ajouterVariante(ideapad8, "8 Go", 350_000, 5);
            p.ajouterVariante(ideapad16, "16 Go", 420_000, 0);
            image(p);
        });
        caracteristiques(ideapad, (em, p) -> {
            valeur(em, p, ramPortables, ideapad8, 8);
            valeur(em, p, ramPortables, ideapad16, 16);
            valeur(em, p, ecran, null, 14);
            valeur(em, p, systemePortables, null, "Windows 11 Famille");
            valeur(em, p, stockage, null, "SSD NVMe");
        });
        latitude = produit("Latitude 5420", dell, portables, p -> {
            p.setEtat(EtatProduit.RECONDITIONNE);
            p.ajouterVariante(latitude32, "32 Go", 300_000, 3);
            image(p);
        });
        caracteristiques(latitude, (em, p) -> {
            valeur(em, p, ramPortables, null, 32);
            valeur(em, p, ecran, null, 14);
            valeur(em, p, systemePortables, null, "Windows 11 Pro");
            // Valeur avec une virgule : un filtre ne doit jamais être découpé sur les virgules
            valeur(em, p, stockage, null, "SSD NVMe, PCIe 4.0");
        });
        Produit ancien = produit("Ancien portable", asus, portables, p -> {
            p.setActif(false);
            p.ajouterVariante(ancien16, "16 Go", 100_000, 9);
            image(p);
        });
        caracteristiques(ancien, (em, p) -> valeur(em, p, ramPortables, ancien16, 16));
        tuf = produit("TUF Gaming A15", asus, gaming, p -> {
            p.setMisEnAvant(true);
            p.ajouterVariante(tuf16, "16 Go", 650_000, 4);
            image(p);
        });
        caracteristiques(tuf, (em, p) -> {
            valeur(em, p, ramGaming, tuf16, 16);
            valeur(em, p, systemeGaming, null, "Windows 11 Famille");
        });
        expertCenter = produit("ExpertCenter D5", asus, bureau, p -> {
            p.setNombreVentes(10);
            p.ajouterVariante(expert16, "16 Go", 500_000, 1);
            image(p);
        });
        caracteristiques(expertCenter, (em, p) -> {
            valeur(em, p, ramBureau, null, 16);
            valeur(em, p, systemeBureau, null, "Windows 11 Pro");
        });
    }

    // --- La variante retenue est la moins chère parmi celles qui conviennent ------------------------

    @Test
    void leFiltreRam16AfficheLePrixDeLaVariante16Go() throws Exception {
        RequeteComptee requete = lireEnComptant(
                "/api/produits?categorie={c}&f.ram_go=16&tri=prix-croissant", portables.getSlug());

        JsonNode page = corps(requete.resultat());
        assertThat(slugs(page)).containsExactly(ideapad.getSlug(), vivobook.getSlug());
        JsonNode carteVivobook = carte(page, vivobook);
        assertThat(carteVivobook.get("prix").asLong()).isEqualTo(449_000);
        assertThat(carteVivobook.get("prixBarre").asLong()).isEqualTo(479_000);
        assertThat(carteVivobook.get("reduction").asInt()).isEqualTo(6);
        assertThat(carteVivobook.get("skuParDefaut").asString()).isEqualTo(vivobook16);
        assertThat(carteVivobook.get("disponibilite").asString()).isEqualTo("STOCK_FAIBLE");
        assertThat(carteVivobook.get("varianteId").asLong()).isEqualTo(idVariante(vivobook16));
        // La variante 16 Go de l'IdeaPad est en rupture : c'est elle qui est montrée, avec sa disponibilité
        assertThat(carte(page, ideapad).get("prix").asLong()).isEqualTo(420_000);
        assertThat(carte(page, ideapad).get("disponibilite").asString()).isEqualTo("RUPTURE");
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        assertThat(requete.requetesSql()).as("requêtes SQL de la liste").isLessThanOrEqualTo(4);
    }

    @Test
    void leFiltreDePrixPorteSurLesPrixDesVariantes() throws Exception {
        JsonNode page = corps(lire("/api/produits?categorie={c}&prixMin=400000&prixMax=500000&tri=prix-croissant",
                portables.getSlug()));

        // Vivobook : prix minimum 389 000 hors plage, mais sa variante 16 Go (449 000) y est
        assertThat(slugs(page)).containsExactly(ideapad.getSlug(), vivobook.getSlug());
        assertThat(carte(page, vivobook).get("prix").asLong()).isEqualTo(449_000);
        assertThat(carte(page, vivobook).get("skuParDefaut").asString()).isEqualTo(vivobook16);
        assertThat(carte(page, ideapad).get("skuParDefaut").asString()).isEqualTo(ideapad16);
    }

    @Test
    void tousLesFiltresDeVarianteDoiventEtreSatisfaitsParLaMemeVariante() throws Exception {
        // IdeaPad : la variante 16 Go est en rupture et la variante en stock a 8 Go → écarté
        JsonNode page = corps(lire("/api/produits?categorie={c}&f.ram_go=16&disponible=true", portables.getSlug()));
        assertThat(slugs(page)).containsExactly(vivobook.getSlug());

        // Sans filtre de variante, la carte montre la variante la moins chère, même en rupture d'autres
        JsonNode sansFiltre = corps(lire("/api/produits?categorie={c}", portables.getSlug()));
        assertThat(carte(sansFiltre, vivobook).get("skuParDefaut").asString()).isEqualTo(vivobook8);
    }

    @Test
    void lesValeursRepeteesSontCombineesEtUneValeurAvecVirguleNestPasDecoupee() throws Exception {
        JsonNode page = corps(lire("/api/produits?categorie={c}&f.ram_go=16&f.ram_go=32&tri=prix-croissant",
                portables.getSlug()));
        // Latitude : 32 Go au niveau du produit, valable pour sa variante
        assertThat(slugs(page)).containsExactly(latitude.getSlug(), ideapad.getSlug(), vivobook.getSlug());

        JsonNode virgule = corps(lire("/api/produits?categorie={c}&f.type_stockage={v}", portables.getSlug(),
                "SSD NVMe, PCIe 4.0"));
        assertThat(slugs(virgule)).containsExactly(latitude.getSlug());

        JsonNode marques = corps(lire("/api/produits?categorie={c}&marque={a}&marque={l}&etat=NEUF",
                portables.getSlug(), asus.getSlug(), lenovo.getSlug()));
        assertThat(slugs(marques)).containsExactlyInAnyOrder(vivobook.getSlug(), ideapad.getSlug());
        assertThat(slugs(corps(lire("/api/produits?categorie={c}&etat=RECONDITIONNE", portables.getSlug()))))
                .containsExactly(latitude.getSlug());
    }

    // --- Catégorie parente, tris et pagination ------------------------------------------------------

    @Test
    void uneCategorieParenteListeSaSousArborescenceDansLOrdreRecommande() throws Exception {
        JsonNode page = corps(lire("/api/produits?categorie={c}", ordinateurs.getSlug()));

        // Mis en avant, puis ventes, puis nouveauté (les plus récents d'abord) ; l'inactif n'apparaît pas
        assertThat(slugs(page)).containsExactly(tuf.getSlug(), expertCenter.getSlug(), latitude.getSlug(),
                ideapad.getSlug(), vivobook.getSlug());
        assertThat(page.get("totalElements").asLong()).isEqualTo(5);
        // Les filtres de caractéristiques communs à toutes les sous-catégories sont acceptés
        assertThat(slugs(corps(lire("/api/produits?categorie={c}&f.systeme={s}", ordinateurs.getSlug(),
                "Windows 11 Pro")))).containsExactlyInAnyOrder(latitude.getSlug(), expertCenter.getSlug());
    }

    @Test
    void lesTrisParPrixUtilisentLePrixAffiche() throws Exception {
        assertThat(slugs(corps(lire("/api/produits?categorie={c}&tri=prix-croissant", portables.getSlug()))))
                .containsExactly(latitude.getSlug(), ideapad.getSlug(), vivobook.getSlug());
        assertThat(slugs(corps(lire("/api/produits?categorie={c}&tri=prix-decroissant", portables.getSlug()))))
                .containsExactly(vivobook.getSlug(), ideapad.getSlug(), latitude.getSlug());
        lire("/api/produits?categorie={c}&tri=pertinence", portables.getSlug())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"))
                .andExpect(jsonPath("$.parametre").value("tri"));
    }

    @Test
    void laPaginationDonneLeTotalMemeAuDelaDeLaDernierePage() throws Exception {
        lire("/api/produits?categorie={c}&taille=2&page=3&tri=prix-croissant", ordinateurs.getSlug())
                .andExpect(jsonPath("$.elements.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(3));
        lire("/api/produits?categorie={c}&taille=2&page=9", ordinateurs.getSlug())
                .andExpect(jsonPath("$.elements.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"f.inconnu=1", "f.ram_go=seize", "f.frequence_hz=144", "tri=au-hasard", "taille=100",
            "page=0", "prixMin=500000&prixMax=100000", "prixMin=-1", "etat=NEUVE", "disponible=oui",
            "tri=prix-croissant&tri=nouveaute"})
    void unParametreInvalideRenvoie400(String parametres) throws Exception {
        lire("/api/produits?categorie=" + portables.getSlug() + "&" + parametres)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
    }

    @Test
    void lesFiltresDeCaracteristiquesDemandentUneCategorieExistante() throws Exception {
        lire("/api/produits?f.ram_go=16").andExpect(status().isBadRequest());
        lire("/api/produits?categorie=categorie-inconnue").andExpect(status().isNotFound());
    }

    // --- Facettes -----------------------------------------------------------------------------------

    @Test
    void lesFacettesIgnorentLeurPropreFiltreEtSuiventLaLogiqueDesVariantes() throws Exception {
        RequeteComptee requete = lireEnComptant("/api/categories/{c}/filtres?f.ram_go=16", portables.getSlug());

        JsonNode filtres = corps(requete.resultat());
        assertThat(filtres.get("totalProduits").asLong()).isEqualTo(2);
        // RAM, calculée sans le filtre RAM : 8 Go (Vivobook, IdeaPad), 16 Go (les mêmes), 32 Go (Latitude)
        assertThat(options(filtres, "ram_go")).containsExactly("8:2:false:8 Go", "16:2:true:16 Go", "32:1:false:32 Go");
        // Les autres facettes appliquent le filtre RAM, variante par variante
        assertThat(marques(filtres)).containsExactlyInAnyOrder(asus.getSlug() + ":1", lenovo.getSlug() + ":1");
        assertThat(filtres.get("prix").get("min").asLong()).isEqualTo(420_000);
        assertThat(filtres.get("prix").get("max").asLong()).isEqualTo(449_000);
        assertThat(filtres.get("disponible").get("enStock").asLong()).isEqualTo(1);
        assertThat(options(filtres, "systeme")).containsExactly("Windows 11 Famille:2:false:Windows 11 Famille");
        assertThat(filtres.get("etats").get(0).get("libelle").asString()).isEqualTo("Neuf");
        assertThat(requete.requetesSql()).as("requêtes SQL des filtres").isLessThanOrEqualTo(4);
    }

    @Test
    void lesFacettesDeCaracteristiquesRespectentLeFiltreDePrixVarianteParVariante() throws Exception {
        JsonNode filtres = corps(lire("/api/categories/{c}/filtres?prixMin=400000&prixMax=500000",
                portables.getSlug()));

        // Seules les variantes 16 Go sont dans la plage : 8 Go et 32 Go disparaissent
        assertThat(options(filtres, "ram_go")).containsExactly("16:2:false:16 Go");
        // Le prix, calculé sans son propre filtre, couvre toutes les variantes actives
        assertThat(filtres.get("prix").get("min").asLong()).isEqualTo(300_000);
        assertThat(filtres.get("prix").get("max").asLong()).isEqualTo(449_000);
        assertThat(options(filtres, "ecran_pouces")).containsExactly("14:1:false:14 pouces", "15.6:1:false:15,6 pouces");
    }

    @Test
    void uneCategorieParenteProposeSesSousCategoriesEtLesClesCommunes() throws Exception {
        JsonNode filtres = corps(lire("/api/categories/{c}/filtres", ordinateurs.getSlug()));

        assertThat(filtres.get("totalProduits").asLong()).isEqualTo(5);
        assertThat(filtres.get("sousCategories").valueStream()
                .map(o -> o.get("libelle").asString() + ":" + o.get("nombre").asLong()).toList())
                .containsExactly("PC portables:3", "PC gaming:1", "PC de bureau:1");
        assertThat(filtres.get("caracteristiques").valueStream().map(c -> c.get("cle").asString()).toList())
                .containsExactly("ram_go", "systeme");
        assertThat(options(filtres, "ram_go")).containsExactly("8:2:false:8 Go", "16:4:false:16 Go", "32:1:false:32 Go");
    }

    // --- Facettes de la page marque -----------------------------------------------------------------

    @Test
    void lesFiltresDUneMarqueProposentSesCategoriesSansCaracteristiques() throws Exception {
        // Asus actifs : Vivobook (portables, 389 000 / 449 000), TUF (gaming, 650 000), ExpertCenter (bureau, 500 000)
        JsonNode filtres = corps(lire("/api/marques/{m}/filtres?marque=" + lenovo.getSlug(), asus.getSlug()));

        assertThat(filtres.get("totalProduits").asLong()).as("le paramètre marque est ignoré").isEqualTo(3);
        assertThat(categoriesProposees(filtres))
                .containsExactly("PC de bureau:1:false", "PC gaming:1:false", "PC portables:1:false");
        assertThat(filtres.get("marques").size()).isZero();
        assertThat(filtres.get("caracteristiques").size()).isZero();
        assertThat(filtres.get("prix").get("min").asLong()).isEqualTo(389_000);
        assertThat(filtres.get("prix").get("max").asLong()).isEqualTo(650_000);
        assertThat(filtres.get("etats").get(0).get("nombre").asLong()).isEqualTo(3);
        assertThat(filtres.get("disponible").get("enStock").asLong()).isEqualTo(3);
    }

    @Test
    void laFacetteDesCategoriesDUneMarqueIgnoreSonPropreFiltre() throws Exception {
        JsonNode filtres = corps(lire("/api/marques/{m}/filtres?categorie=" + portables.getSlug(), asus.getSlug()));

        assertThat(filtres.get("totalProduits").asLong()).isEqualTo(1);
        assertThat(categoriesProposees(filtres))
                .containsExactly("PC de bureau:1:false", "PC gaming:1:false", "PC portables:1:true");
        // Les autres facettes appliquent le filtre de catégorie
        assertThat(filtres.get("prix").get("max").asLong()).isEqualTo(449_000);

        // Catégorie parente choisie : elle reste affichée, cochée, avec le total de ses descendants
        JsonNode parente = corps(lire("/api/marques/{m}/filtres?categorie=" + ordinateurs.getSlug(), asus.getSlug()));
        assertThat(categoriesProposees(parente)).first().isEqualTo("Ordinateurs:3:true");
    }

    @Test
    void lesFiltresDUneMarqueRefusentLesCaracteristiquesEtLesMarquesInconnues() throws Exception {
        lire("/api/marques/{m}/filtres?f.ram_go=16", asus.getSlug())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
        lire("/api/marques/{m}/filtres", "marque-inconnue").andExpect(status().isNotFound());
        lire("/api/marques/{m}/filtres?categorie=categorie-inconnue", asus.getSlug()).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------------------

    /** Catégories proposées : « libellé:nombre:sélectionnée ». */
    private static List<String> categoriesProposees(JsonNode filtres) {
        return filtres.get("sousCategories").valueStream()
                .map(o -> "%s:%d:%s".formatted(o.get("libelle").asString(), o.get("nombre").asLong(),
                        o.get("selectionne").asBoolean()))
                .toList();
    }

    private static void image(Produit produit) {
        produit.ajouterMedia(TypeMedia.IMAGE, "https://exemple.sn/" + produit.getSlug() + ".jpg", produit.getNom());
    }

    private long idVariante(String sku) {
        return enTransaction(em -> em.createQuery("select v.id from VarianteProduit v where v.sku = :sku", Long.class)
                .setParameter("sku", sku).getSingleResult());
    }

    private static List<String> slugs(JsonNode page) {
        return page.get("elements").valueStream().map(carte -> carte.get("slug").asString()).toList();
    }

    private static JsonNode carte(JsonNode page, Produit produit) {
        return page.get("elements").valueStream()
                .filter(carte -> carte.get("slug").asString().equals(produit.getSlug())).findFirst().orElseThrow();
    }

    private static List<String> marques(JsonNode filtres) {
        return filtres.get("marques").valueStream()
                .map(o -> o.get("valeur").asString() + ":" + o.get("nombre").asLong()).toList();
    }

    /** Valeurs d'une facette de caractéristique : « valeur:nombre:sélectionnée:libellé ». */
    private static List<String> options(JsonNode filtres, String cle) {
        return filtres.get("caracteristiques").valueStream()
                .filter(c -> c.get("cle").asString().equals(cle))
                .flatMap(c -> c.get("valeurs").valueStream())
                .map(o -> "%s:%d:%s:%s".formatted(o.get("valeur").asString(), o.get("nombre").asLong(),
                        o.get("selectionne").asBoolean(), o.get("libelle").asString()))
                .toList();
    }
}
