package sn.boutique.stock.depot;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.stock.modele.MouvementStock;

public interface MouvementStockDepot extends JpaRepository<MouvementStock, Long> {

    Page<MouvementStock> findByVarianteIdOrderByDateCreationDesc(Long varianteId, Pageable pageable);
}
