package sn.boutique.compte.depot;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.compte.modele.ChangementEmail;
import sn.boutique.compte.modele.ChangementEmail.Statut;

public interface ChangementEmailDepot extends JpaRepository<ChangementEmail, Long> {

    @Query("select c from ChangementEmail c join fetch c.utilisateur where c.empreinteConfirmation = :empreinte")
    Optional<ChangementEmail> trouverParConfirmation(String empreinte);

    @Query("select c from ChangementEmail c join fetch c.utilisateur where c.empreinteSignalement = :empreinte")
    Optional<ChangementEmail> trouverParSignalement(String empreinte);

    Optional<ChangementEmail> findByUtilisateurIdAndStatut(Long utilisateurId, Statut statut);
}
