package sn.boutique.catalogue.depot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.catalogue.modele.MediaProduit;

public interface MediaProduitDepot extends JpaRepository<MediaProduit, Long> {

    List<MediaProduit> findByProduitIdOrderByPositionAsc(Long produitId);
}
