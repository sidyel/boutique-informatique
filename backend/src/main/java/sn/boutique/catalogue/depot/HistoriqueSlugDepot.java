package sn.boutique.catalogue.depot;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.catalogue.modele.HistoriqueSlug;
import sn.boutique.catalogue.modele.TypeEntiteSlug;

public interface HistoriqueSlugDepot extends JpaRepository<HistoriqueSlug, Long> {

    Optional<HistoriqueSlug> findByTypeEntiteAndAncienSlug(TypeEntiteSlug typeEntite, String ancienSlug);
}
