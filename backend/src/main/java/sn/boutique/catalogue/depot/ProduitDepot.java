package sn.boutique.catalogue.depot;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.catalogue.modele.Produit;

public interface ProduitDepot extends JpaRepository<Produit, Long> {

    Optional<Produit> findBySlugAndActifTrue(String slug);

    boolean existsBySlug(String slug);
}
