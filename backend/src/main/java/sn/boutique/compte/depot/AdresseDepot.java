package sn.boutique.compte.depot;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.compte.modele.Adresse;

public interface AdresseDepot extends JpaRepository<Adresse, Long> {

    List<Adresse> findByUtilisateurIdOrderByParDefautDescDateCreationAsc(Long utilisateurId);

    /** Une adresse n'est jamais lue ni modifiée par un autre compte que le sien : mêmes réponses qu'un identifiant inconnu. */
    Optional<Adresse> findByIdAndUtilisateurId(Long id, Long utilisateurId);

    long countByUtilisateurId(Long utilisateurId);

    /** L'index unique n'autorise qu'une adresse par défaut par compte : on retire l'ancienne avant de poser la nouvelle. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Adresse a set a.parDefaut = false where a.utilisateur.id = :utilisateurId and a.parDefaut = true")
    int retirerParDefaut(Long utilisateurId);

    Optional<Adresse> findFirstByUtilisateurIdOrderByDateCreationDesc(Long utilisateurId);
}
