package sn.boutique.authentification.depot;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.authentification.modele.JetonVerificationEmail;

public interface JetonVerificationEmailDepot extends JpaRepository<JetonVerificationEmail, Long> {

    Optional<JetonVerificationEmail> findByEmpreinte(String empreinte);

    /** Un nouvel envoi invalide les jetons précédents encore valables. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JetonVerificationEmail j set j.dateUtilisation = :maintenant where j.utilisateur.id = :utilisateurId and j.dateUtilisation is null")
    int invaliderPourUtilisateur(Long utilisateurId, Instant maintenant);

    @Modifying
    @Query("delete from JetonVerificationEmail j where j.dateExpiration < :limite")
    int supprimerExpiresAvant(Instant limite);
}
