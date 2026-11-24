package sn.boutique.marketing.depot;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.marketing.modele.Collection;

public interface CollectionDepot extends JpaRepository<Collection, Long> {

    Optional<Collection> findBySlugAndActifTrue(String slug);

    boolean existsBySlug(String slug);
}
