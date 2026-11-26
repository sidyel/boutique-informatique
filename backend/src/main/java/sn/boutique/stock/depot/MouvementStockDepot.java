package sn.boutique.stock.depot;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.stock.modele.MouvementStock;

public interface MouvementStockDepot extends JpaRepository<MouvementStock, Long> {

    Page<MouvementStock> findByVarianteIdOrderByDateCreationDesc(Long varianteId, Pageable pageable);

    /** Un mouvement de ce type existe deja pour la commande (une liberation ne se fait qu une fois). */
    boolean existsByCommandeIdAndType(Long commandeId, sn.boutique.stock.modele.TypeMouvementStock type);
}
