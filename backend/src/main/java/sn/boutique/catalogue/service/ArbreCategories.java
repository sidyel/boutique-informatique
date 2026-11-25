package sn.boutique.catalogue.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import sn.boutique.catalogue.dto.CategorieNoeudReponse;
import sn.boutique.catalogue.dto.LienReponse;

/**
 * Arbre des catégories actives, construit en mémoire à partir d'une seule requête.
 * <p>
 * Une catégorie n'y figure que si elle et tous ses ancêtres sont actifs : désactiver une catégorie
 * masque toute sa sous-arborescence. Le nombre de produits d'un nœud inclut ses descendants.
 */
public final class ArbreCategories {

    /** Ligne lue en base : catégorie active et nombre de produits actifs qui lui sont rattachés directement. */
    public record Ligne(long id, Long parentId, String nom, String slug, String imageUrl, long produitsDirects) {
    }

    public static final class Noeud {
        private final Ligne ligne;
        private final Noeud parent;
        private final List<Noeud> enfants = new ArrayList<>();
        private long nombreProduits;

        private Noeud(Ligne ligne, Noeud parent) {
            this.ligne = ligne;
            this.parent = parent;
        }

        public long id() {
            return ligne.id();
        }

        public String nom() {
            return ligne.nom();
        }

        public String slug() {
            return ligne.slug();
        }

        public long nombreProduits() {
            return nombreProduits;
        }

        public Optional<Noeud> parent() {
            return Optional.ofNullable(parent);
        }

        public List<Noeud> enfants() {
            return Collections.unmodifiableList(enfants);
        }

        /** Ancêtres, de la racine au parent direct. */
        public List<LienReponse> filAriane() {
            List<LienReponse> chemin = new ArrayList<>();
            for (Noeud n = parent; n != null; n = n.parent) {
                chemin.addFirst(new LienReponse(n.nom(), n.slug()));
            }
            return chemin;
        }

        /** Identifiants du nœud et de tous ses descendants. */
        public List<Long> idsSousArbre() {
            List<Long> ids = new ArrayList<>();
            ajouterIds(this, ids);
            return ids;
        }

        public CategorieNoeudReponse versReponse() {
            return new CategorieNoeudReponse(nom(), slug(), ligne.imageUrl(), nombreProduits,
                    enfants.stream().map(Noeud::versReponse).toList());
        }

        private static void ajouterIds(Noeud noeud, List<Long> ids) {
            ids.add(noeud.id());
            noeud.enfants.forEach(enfant -> ajouterIds(enfant, ids));
        }

        private long calculerTotal() {
            nombreProduits = ligne.produitsDirects() + enfants.stream().mapToLong(Noeud::calculerTotal).sum();
            return nombreProduits;
        }
    }

    private final List<Noeud> racines = new ArrayList<>();
    private final Map<String, Noeud> parSlug = new HashMap<>();

    /** @param lignes catégories actives, déjà dans l'ordre d'affichage (position, nom) */
    public ArbreCategories(List<Ligne> lignes) {
        Map<Long, List<Ligne>> parParent = new LinkedHashMap<>();
        for (Ligne ligne : lignes) {
            parParent.computeIfAbsent(ligne.parentId(), cle -> new ArrayList<>()).add(ligne);
        }
        // Une catégorie dont le parent est inactif n'est rattachée à aucune racine : elle reste invisible.
        for (Ligne ligne : parParent.getOrDefault(null, List.of())) {
            racines.add(construire(ligne, null, parParent));
        }
        racines.forEach(Noeud::calculerTotal);
    }

    public List<Noeud> racines() {
        return Collections.unmodifiableList(racines);
    }

    /** Catégorie visible (elle et tous ses ancêtres sont actifs). */
    public Optional<Noeud> trouver(String slug) {
        return Optional.ofNullable(parSlug.get(slug));
    }

    public List<CategorieNoeudReponse> versReponse() {
        return racines.stream().map(Noeud::versReponse).toList();
    }

    private Noeud construire(Ligne ligne, Noeud parent, Map<Long, List<Ligne>> parParent) {
        Noeud noeud = new Noeud(ligne, parent);
        parSlug.put(ligne.slug(), noeud);
        for (Ligne enfant : parParent.getOrDefault(ligne.id(), List.of())) {
            noeud.enfants.add(construire(enfant, noeud, parParent));
        }
        return noeud;
    }
}
