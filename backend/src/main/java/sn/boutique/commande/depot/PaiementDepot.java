package sn.boutique.commande.depot;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.commande.modele.Paiement;
import sn.boutique.commande.modele.StatutPaiement;
import sn.boutique.commande.modele.TypeFournisseurPaiement;

public interface PaiementDepot extends JpaRepository<Paiement, Long> {

    List<Paiement> findByCommandeIdOrderByDateCreationDesc(Long commandeId);

    Optional<Paiement> findByFournisseurAndReferenceExterne(TypeFournisseurPaiement fournisseur, String referenceExterne);

    /** Numéro de la commande d'un paiement, sans charger le paiement (qui doit être lu après le verrou). */
    @Query("select p.commande.numero from Paiement p where p.id = :id")
    Optional<String> numeroCommande(Long id);

    Page<Paiement> findByStatutOrderByDateCreationAsc(StatutPaiement statut, Pageable pageable);
}
