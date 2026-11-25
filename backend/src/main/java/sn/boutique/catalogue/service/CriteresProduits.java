package sn.boutique.catalogue.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springframework.util.MultiValueMap;
import sn.boutique.catalogue.modele.EtatProduit;
import sn.boutique.noyau.erreur.ParametreInvalideException;

/**
 * Critères d'une liste de produits, lus depuis les paramètres de la requête.
 * <p>
 * Les filtres à valeurs multiples sont des <strong>paramètres répétés</strong> :
 * {@code marque=asus&marque=lenovo&f.ram_go=16&f.ram_go=32}. Une valeur n'est jamais découpée sur les
 * virgules (une valeur texte peut en contenir). Les valeurs sont normalisées (triées, nombres sans zéros
 * inutiles) : deux requêtes équivalentes ont les mêmes critères, donc la même clé de cache.
 *
 * @param promotion        seulement les produits dont la variante retenue a un prix barré ({@code promotion=true})
 * @param caracteristiques valeurs acceptées par clé de caractéristique (filtre {@code f.<cle>})
 * @param tri              {@code null} : tri par défaut (recommandé, ou pertinence avec une recherche)
 * @param page             numéro de page, à partir de 1
 */
public record CriteresProduits(
        String categorie,
        Set<String> marques,
        Set<EtatProduit> etats,
        Long prixMin,
        Long prixMax,
        boolean disponible,
        boolean promotion,
        Map<String, Set<String>> caracteristiques,
        TriProduits tri,
        int page,
        int taille) {

    public static final int TAILLE_PAR_DEFAUT = 24;
    public static final int TAILLE_MAX = 48;
    public static final int PAGE_MAX = 500;
    private static final int VALEURS_MAX = 20;
    private static final int CARACTERISTIQUES_MAX = 15;
    private static final String PREFIXE_CARACTERISTIQUE = "f.";
    private static final Pattern CLE = Pattern.compile("[a-z][a-z0-9_]{0,59}");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,219}");

    /** Familles de filtres, pour le calcul des facettes (chaque facette ignore son propre filtre). */
    public enum Famille { MARQUE, ETAT, PRIX, DISPONIBLE, CARACTERISTIQUE }

    public boolean aDesFiltresDeVariante() {
        return prixMin != null || prixMax != null || disponible || promotion || !caracteristiques.isEmpty();
    }

    public long decalage() {
        return (long) (page - 1) * taille;
    }

    /** Mêmes critères sans le filtre de cette famille (pour {@code cle} : sans cette seule caractéristique). */
    public CriteresProduits sans(Famille famille, String cle) {
        return switch (famille) {
            case MARQUE -> new CriteresProduits(categorie, Set.of(), etats, prixMin, prixMax, disponible,
                    promotion, caracteristiques, tri, page, taille);
            case ETAT -> new CriteresProduits(categorie, marques, Set.of(), prixMin, prixMax, disponible,
                    promotion, caracteristiques, tri, page, taille);
            case PRIX -> new CriteresProduits(categorie, marques, etats, null, null, disponible,
                    promotion, caracteristiques, tri, page, taille);
            case DISPONIBLE -> new CriteresProduits(categorie, marques, etats, prixMin, prixMax, false,
                    promotion, caracteristiques, tri, page, taille);
            case CARACTERISTIQUE -> {
                Map<String, Set<String>> autres = new TreeMap<>(caracteristiques);
                autres.remove(cle);
                yield new CriteresProduits(categorie, marques, etats, prixMin, prixMax, disponible,
                        promotion, Map.copyOf(autres), tri, page, taille);
            }
        };
    }

    /**
     * Clé de cache non ambiguë : chaque valeur est encodée comme dans une URL et les ensembles sont triés,
     * deux critères équivalents donnent la même clé et deux critères différents des clés différentes.
     *
     * @param avecPagination {@code false} pour les facettes, qui ne dépendent ni du tri ni de la page
     */
    public String cleCache(boolean avecPagination) {
        StringBuilder cle = new StringBuilder();
        ajouter(cle, "categorie", categorie);
        marques.stream().sorted().forEach(marque -> ajouter(cle, "marque", marque));
        etats.stream().map(Enum::name).sorted().forEach(etat -> ajouter(cle, "etat", etat));
        ajouter(cle, "prixMin", prixMin);
        ajouter(cle, "prixMax", prixMax);
        ajouter(cle, "disponible", disponible ? "true" : null);
        ajouter(cle, "promotion", promotion ? "true" : null);
        new TreeMap<>(caracteristiques).forEach((nom, valeurs) ->
                valeurs.stream().sorted().forEach(valeur -> ajouter(cle, PREFIXE_CARACTERISTIQUE + nom, valeur)));
        if (avecPagination) {
            ajouter(cle, "tri", tri == null ? null : tri.name());
            ajouter(cle, "page", page);
            ajouter(cle, "taille", taille);
        }
        return cle.toString();
    }

    private static void ajouter(StringBuilder cle, String nom, Object valeur) {
        if (valeur != null) {
            cle.append(cle.isEmpty() ? "" : "&").append(nom).append('=')
                    .append(java.net.URLEncoder.encode(valeur.toString(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    public CriteresProduits avecCategorie(String slug) {
        return new CriteresProduits(slug, marques, etats, prixMin, prixMax, disponible, promotion, caracteristiques,
                tri, page, taille);
    }

    /** Lecture et validation des paramètres ; toute valeur invalide donne une erreur 400 explicite. */
    public static CriteresProduits depuis(MultiValueMap<String, String> parametres) {
        String categorie = unique(parametres, "categorie");
        if (categorie != null && !SLUG.matcher(categorie).matches()) {
            throw new ParametreInvalideException("categorie", "Catégorie invalide : « %s ».".formatted(categorie));
        }
        Set<String> marques = new java.util.TreeSet<>();
        for (String marque : valeurs(parametres, "marque")) {
            if (!SLUG.matcher(marque).matches()) {
                throw new ParametreInvalideException("marque", "Marque invalide : « %s ».".formatted(marque));
            }
            marques.add(marque);
        }
        Set<EtatProduit> etats = java.util.EnumSet.noneOf(EtatProduit.class);
        for (String etat : valeurs(parametres, "etat")) {
            try {
                etats.add(EtatProduit.valueOf(etat.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new ParametreInvalideException("etat",
                        "État inconnu : « %s » (NEUF, RECONDITIONNE ou OCCASION).".formatted(etat));
            }
        }
        Long prixMin = montant(parametres, "prixMin");
        Long prixMax = montant(parametres, "prixMax");
        if (prixMin != null && prixMax != null && prixMin > prixMax) {
            throw new ParametreInvalideException("prixMin", "Le prix minimum dépasse le prix maximum.");
        }
        String disponible = unique(parametres, "disponible");
        if (disponible != null && !disponible.equals("true") && !disponible.equals("false")) {
            throw new ParametreInvalideException("disponible", "« disponible » vaut true ou false.");
        }
        String promotion = unique(parametres, "promotion");
        if (promotion != null && !promotion.equals("true") && !promotion.equals("false")) {
            throw new ParametreInvalideException("promotion", "« promotion » vaut true ou false.");
        }
        String codeTri = unique(parametres, "tri");
        TriProduits tri = codeTri == null ? null : TriProduits.depuisCode(codeTri)
                .orElseThrow(() -> new ParametreInvalideException("tri", "Tri inconnu : « %s ».".formatted(codeTri)));
        int page = entier(parametres, "page", 1, 1, PAGE_MAX);
        int taille = entier(parametres, "taille", TAILLE_PAR_DEFAUT, 1, TAILLE_MAX);
        return new CriteresProduits(categorie, Set.copyOf(marques), Set.copyOf(etats), prixMin, prixMax,
                "true".equals(disponible), "true".equals(promotion), caracteristiques(parametres), tri, page, taille);
    }

    /** Valeur normalisée d'un filtre de caractéristique : un nombre perd ses zéros inutiles (« 16.0 » → « 16 »). */
    public static String normaliserValeur(String valeur) {
        String nettoyee = valeur.strip();
        if (nettoyee.matches("-?\\d+(\\.\\d+)?")) {
            return MiseEnFormeCatalogue.nombreBrut(new BigDecimal(nettoyee));
        }
        return nettoyee;
    }

    private static Map<String, Set<String>> caracteristiques(MultiValueMap<String, String> parametres) {
        Map<String, Set<String>> resultat = new TreeMap<>();
        for (Map.Entry<String, List<String>> parametre : parametres.entrySet()) {
            if (!parametre.getKey().startsWith(PREFIXE_CARACTERISTIQUE)) {
                continue;
            }
            String cle = parametre.getKey().substring(PREFIXE_CARACTERISTIQUE.length());
            if (!CLE.matcher(cle).matches()) {
                throw new ParametreInvalideException(parametre.getKey(), "Filtre invalide : « %s ».".formatted(cle));
            }
            Set<String> valeurs = new java.util.TreeSet<>();
            for (String valeur : parametre.getValue()) {
                String normalisee = normaliserValeur(valeur);
                if (normalisee.isEmpty() || normalisee.length() > 255) {
                    throw new ParametreInvalideException(parametre.getKey(), "Valeur de filtre invalide.");
                }
                valeurs.add(normalisee);
            }
            if (valeurs.size() > VALEURS_MAX) {
                throw new ParametreInvalideException(parametre.getKey(), "Trop de valeurs pour ce filtre.");
            }
            resultat.put(cle, Set.copyOf(valeurs));
        }
        if (resultat.size() > CARACTERISTIQUES_MAX) {
            throw new ParametreInvalideException("f", "Trop de filtres de caractéristiques.");
        }
        return trier(resultat);
    }

    private static Map<String, Set<String>> trier(Map<String, Set<String>> caracteristiques) {
        Map<String, Set<String>> triees = new LinkedHashMap<>();
        caracteristiques.forEach((cle, valeurs) -> triees.put(cle, new LinkedHashSet<>(new java.util.TreeSet<>(valeurs))));
        return java.util.Collections.unmodifiableMap(triees);
    }

    private static List<String> valeurs(MultiValueMap<String, String> parametres, String nom) {
        List<String> valeurs = new ArrayList<>();
        for (String valeur : parametres.getOrDefault(nom, List.of())) {
            if (valeur != null && !valeur.isBlank()) {
                valeurs.add(valeur.strip());
            }
        }
        if (valeurs.size() > VALEURS_MAX) {
            throw new ParametreInvalideException(nom, "Trop de valeurs pour « %s ».".formatted(nom));
        }
        return valeurs;
    }

    private static String unique(MultiValueMap<String, String> parametres, String nom) {
        List<String> valeurs = valeurs(parametres, nom);
        if (valeurs.size() > 1) {
            throw new ParametreInvalideException(nom, "Le paramètre « %s » ne peut être donné qu'une fois.".formatted(nom));
        }
        return valeurs.isEmpty() ? null : valeurs.getFirst();
    }

    private static Long montant(MultiValueMap<String, String> parametres, String nom) {
        String valeur = unique(parametres, nom);
        if (valeur == null) {
            return null;
        }
        try {
            long montant = Long.parseLong(valeur);
            if (montant < 0) {
                throw new NumberFormatException();
            }
            return montant;
        } catch (NumberFormatException e) {
            throw new ParametreInvalideException(nom, "« %s » est un montant entier en FCFA.".formatted(nom));
        }
    }

    private static int entier(MultiValueMap<String, String> parametres, String nom, int defaut, int min, int max) {
        String valeur = unique(parametres, nom);
        if (valeur == null) {
            return defaut;
        }
        try {
            int entier = Integer.parseInt(valeur);
            if (entier < min || entier > max) {
                throw new NumberFormatException();
            }
            return entier;
        } catch (NumberFormatException e) {
            throw new ParametreInvalideException(nom, "« %s » est un entier entre %d et %d.".formatted(nom, min, max));
        }
    }
}
