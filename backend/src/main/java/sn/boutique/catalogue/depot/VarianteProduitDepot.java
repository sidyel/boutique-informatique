package sn.boutique.catalogue.depot;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.catalogue.modele.VarianteProduit;

public interface VarianteProduitDepot extends JpaRepository<VarianteProduit, Long> {

    Optional<VarianteProduit> findBySku(String sku);

    List<VarianteProduit> findByProduitIdAndActifTrueOrderByPrixAscIdAsc(Long produitId);
}
