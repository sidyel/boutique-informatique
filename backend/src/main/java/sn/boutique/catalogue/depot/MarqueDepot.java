package sn.boutique.catalogue.depot;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.catalogue.modele.Marque;

public interface MarqueDepot extends JpaRepository<Marque, Long> {

    Optional<Marque> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
