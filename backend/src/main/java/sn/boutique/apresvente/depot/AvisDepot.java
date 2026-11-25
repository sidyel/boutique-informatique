package sn.boutique.apresvente.depot;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.apresvente.modele.Avis;
import sn.boutique.apresvente.modele.StatutAvis;

public interface AvisDepot extends JpaRepository<Avis, Long> {

    Page<Avis> findByProduitIdAndStatutOrderByDateCreationDesc(Long produitId, StatutAvis statut, Pageable pageable);

    boolean existsByProduitIdAndUtilisateurId(Long produitId, Long utilisateurId);

    /** Avis approuvés d'un produit, les plus récents d'abord (fiche produit, sans charger les comptes). */
    @Query(value = "select a from Avis a where a.produit.id = :produitId and a.statut = sn.boutique.apresvente.modele.StatutAvis.APPROUVE"
            + " order by a.dateCreation desc, a.id desc",
            countQuery = "select count(a) from Avis a where a.produit.id = :produitId and a.statut = sn.boutique.apresvente.modele.StatutAvis.APPROUVE")
    Page<Avis> trouverApprouves(Long produitId, Pageable pageable);
}
