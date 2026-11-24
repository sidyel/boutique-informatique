package sn.boutique.catalogue.depot;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.catalogue.modele.Categorie;

public interface CategorieDepot extends JpaRepository<Categorie, Long> {

    Optional<Categorie> findBySlugAndActifTrue(String slug);

    Optional<Categorie> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Racines de l'arbre des catégories, dans l'ordre d'affichage. */
    List<Categorie> findByParentIsNullAndActifTrueOrderByPositionAscNomAsc();
}
