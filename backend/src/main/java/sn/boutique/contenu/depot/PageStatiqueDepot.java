package sn.boutique.contenu.depot;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.contenu.modele.PageStatique;

public interface PageStatiqueDepot extends JpaRepository<PageStatique, Long> {

    Optional<PageStatique> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
