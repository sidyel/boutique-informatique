package sn.boutique.commande.depot;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.commande.modele.Paiement;
import sn.boutique.commande.modele.TypeFournisseurPaiement;

public interface PaiementDepot extends JpaRepository<Paiement, Long> {

    List<Paiement> findByCommandeIdOrderByDateCreationDesc(Long commandeId);

    Optional<Paiement> findByFournisseurAndReferenceExterne(TypeFournisseurPaiement fournisseur, String referenceExterne);
}
