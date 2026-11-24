package sn.boutique.panier.depot;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.panier.modele.Panier;

public interface PanierDepot extends JpaRepository<Panier, Long> {

    Optional<Panier> findByUtilisateurId(Long utilisateurId);

    Optional<Panier> findByJetonInvite(String jetonInvite);
}
