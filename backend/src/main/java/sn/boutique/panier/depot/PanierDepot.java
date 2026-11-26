package sn.boutique.panier.depot;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.panier.modele.Panier;

public interface PanierDepot extends JpaRepository<Panier, Long> {

    Optional<Panier> findByUtilisateurId(Long utilisateurId);

    Optional<Panier> findByJetonInvite(String jetonInvite);

    /** Panier verrouillé : une commande et une modification du panier ne s'exécutent jamais en même temps. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Panier p where p.id = :id")
    Optional<Panier> findByIdPourMiseAJour(Long id);
}
