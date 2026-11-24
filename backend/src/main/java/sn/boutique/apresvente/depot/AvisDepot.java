package sn.boutique.apresvente.depot;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.apresvente.modele.Avis;
import sn.boutique.apresvente.modele.StatutAvis;

public interface AvisDepot extends JpaRepository<Avis, Long> {

    Page<Avis> findByProduitIdAndStatutOrderByDateCreationDesc(Long produitId, StatutAvis statut, Pageable pageable);

    boolean existsByProduitIdAndUtilisateurId(Long produitId, Long utilisateurId);
}
