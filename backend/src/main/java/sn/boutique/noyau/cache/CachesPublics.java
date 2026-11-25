package sn.boutique.noyau.cache;

import java.util.List;

/**
 * Noms des caches des lectures publiques. Chacun a son propre sérialiseur JSON typé (voir {@code CacheConfig}) :
 * on n'y met que des DTO, jamais d'entités, et sans typage par défaut de Jackson (pas de nom de classe
 * dans les données, donc pas de désérialisation arbitraire).
 * <p>
 * La recherche plein texte n'est pas mise en cache : saisie libre, clés trop dispersées.
 */
public final class CachesPublics {

    public static final String ARBRE_CATEGORIES = "arbre-categories";
    public static final String CATEGORIE = "categorie";
    public static final String MARQUES = "marques";
    public static final String MARQUE = "marque";
    public static final String PARAMETRES = "parametres";
    /** Bannières actives d'un emplacement, avant le filtre des dates (appliqué à chaque lecture). */
    public static final String BANNIERES = "bannieres";
    public static final String FICHE_PRODUIT = "fiche-produit";
    public static final String PRODUITS_SIMILAIRES = "produits-similaires";
    public static final String LISTE_PRODUITS = "liste-produits";
    public static final String FILTRES = "filtres";
    /** Sections de produits de l'accueil (nouveautés, meilleures ventes ou sélection, promotions). */
    public static final String ACCUEIL = "accueil";

    public static final List<String> TOUS = List.of(ARBRE_CATEGORIES, CATEGORIE, MARQUES, MARQUE, PARAMETRES,
            BANNIERES, FICHE_PRODUIT, PRODUITS_SIMILAIRES, LISTE_PRODUITS, FILTRES, ACCUEIL);

    private CachesPublics() {
    }
}
