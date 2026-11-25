package sn.boutique.demo;

import java.util.List;
import java.util.Map;

import sn.boutique.catalogue.modele.EtatProduit;
import sn.boutique.catalogue.modele.TypeCaracteristique;
import sn.boutique.contenu.modele.ElementConfiance;
import sn.boutique.marketing.modele.EmplacementBanniere;
import sn.boutique.marketing.modele.TypeCodePromo;

/**
 * Structure des fichiers JSON de {@code src/main/resources/demo/}. Les références entre objets
 * se font par slug (marque, catégorie, produit) ; une référence inconnue arrête le chargement.
 */
final class DonneesDemo {

    private DonneesDemo() {
    }

    record Catalogue(List<MarqueDemo> marques, List<CategorieDemo> categories, List<ProduitDemo> produits) {
    }

    record MarqueDemo(String slug, String nom) {
    }

    record CategorieDemo(String slug, String nom, String parent, int position, String image, String categorieGoogle,
                         String description, List<DefinitionDemo> caracteristiques) {
    }

    /** {@code ordreCarte} (1 à 4) et {@code libelleCourt} : caractéristique affichée sur la carte produit. */
    record DefinitionDemo(String cle, String libelle, TypeCaracteristique type, String unite, String groupe,
                          boolean filtrable, boolean cleRecherche, Integer ordreCarte, String libelleCourt) {
    }

    /** {@code caracteristiques} : clé de la définition → valeur (texte, nombre ou booléen selon le type). */
    record ProduitDemo(String slug, String nom, String marque, String categorie, EtatProduit etat, int garantieMois,
                       String mpn, boolean misEnAvant, String descriptionCourte, String descriptionLongue, String image,
                       Map<String, Object> caracteristiques, List<VarianteDemo> variantes) {
    }

    /** {@code caracteristiques} : valeurs propres à la variante (surchargent celles du produit). */
    record VarianteDemo(String sku, String libelle, Map<String, String> attributs, long prix, Long prixBarre, int stock,
                        Integer poidsGrammes, Map<String, Object> caracteristiques) {
    }

    record Site(ParametresDemo parametres, List<ZoneDemo> zones, List<PageDemo> pages, List<BanniereDemo> bannieres,
                List<CodePromoDemo> codesPromo, List<ArticleDemo> articles, List<CollectionDemo> collections) {
    }

    record ParametresDemo(String telephone, String whatsapp, String email, String adresse, String horaires,
                          Map<String, String> liensReseauxSociaux, String messageBarreSuperieure,
                          List<ElementConfiance> elementsConfiance, String delaiLivraison) {
    }

    record ZoneDemo(String nom, long tarif, int delaiJours, List<String> villesQuartiers) {
    }

    record PageDemo(String slug, String titre, String contenu) {
    }

    /** {@code image} : nom de base ; les fichiers {@code -ordinateur.svg} et {@code -mobile.svg} sont utilisés. */
    record BanniereDemo(EmplacementBanniere emplacement, String image, String texteAlternatif, String titre,
                        String sousTitre, String texteBouton, String lien, int position) {
    }

    record CodePromoDemo(String code, TypeCodePromo type, long valeur, long montantMinimum, Integer utilisationsMax,
                         Integer utilisationsParClient) {
    }

    record ArticleDemo(String slug, String titre, String extrait, String contenu, String image, String categorie,
                       List<String> produitsLies) {
    }

    record CollectionDemo(String slug, String titre, String description, boolean indexable, List<String> produits) {
    }
}
