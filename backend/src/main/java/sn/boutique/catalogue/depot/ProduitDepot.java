package sn.boutique.catalogue.depot;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.catalogue.modele.Produit;

public interface ProduitDepot extends JpaRepository<Produit, Long> {

    Optional<Produit> findBySlugAndActifTrue(String slug);

    /** Produit publié, avec sa marque et sa catégorie (fiche produit : une requête). */
    @Query("select p from Produit p join fetch p.marque join fetch p.categorie where p.slug = :slug and p.actif")
    Optional<Produit> trouverPublieParSlug(String slug);

    Optional<Produit> findBySlug(String slug);

    /** Verrou en écriture sur le produit : sérialise les ajouts de médias (limite du nombre d'images). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produit p where p.id = :id")
    Optional<Produit> verrouiller(Long id);

    boolean existsBySlug(String slug);

    /**
     * Recherche plein texte (V7) : syntaxe « web » (mots, "expression exacte", -exclusion),
     * insensible aux accents et à la casse, sur le vecteur pondéré nom > marque/MPN >
     * catégories/caractéristiques clés > description courte. Classement par pertinence.
     * La pagination et les filtres arrivent avec le service de recherche (phase 2).
     */
    @Query(value = """
            select p.* from produit p
             where p.actif
               and p.vecteur_recherche @@ websearch_to_tsquery('francais_sans_accents', :texte)
             order by ts_rank_cd(p.vecteur_recherche, websearch_to_tsquery('francais_sans_accents', :texte), 32) desc,
                      p.id
             limit :limite
            """, nativeQuery = true)
    List<Produit> rechercher(String texte, int limite);

    /**
     * Repli tolérant aux fautes de frappe (« lenvo ideapad ») et autocomplétion, par trigrammes
     * sur le nom du produit et de sa marque (pg_trgm, seuil word_similarity par défaut 0,6).
     */
    @Query(value = """
            select p.* from produit p
              join marque m on m.id = p.marque_id
             where p.actif
               and (sans_accents(lower(:texte)) <% sans_accents(lower(p.nom))
                    or sans_accents(lower(:texte)) <% sans_accents(lower(m.nom)))
             order by greatest(word_similarity(sans_accents(lower(:texte)), sans_accents(lower(p.nom))),
                               word_similarity(sans_accents(lower(:texte)), sans_accents(lower(m.nom)))) desc,
                      p.id
             limit :limite
            """, nativeQuery = true)
    List<Produit> suggerer(String texte, int limite);
}
