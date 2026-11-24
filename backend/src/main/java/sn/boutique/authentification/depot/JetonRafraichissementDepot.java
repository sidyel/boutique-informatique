package sn.boutique.authentification.depot;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.authentification.modele.JetonRafraichissement;

public interface JetonRafraichissementDepot extends JpaRepository<JetonRafraichissement, Long> {

    Optional<JetonRafraichissement> findByEmpreinte(String empreinte);

    /** Réutilisation détectée ou déconnexion : toute la famille devient inutilisable. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JetonRafraichissement j set j.revoque = true where j.famille = :famille and j.revoque = false")
    int revoquerFamille(UUID famille);

    /** « Se déconnecter de tous les appareils », changement de mot de passe. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JetonRafraichissement j set j.revoque = true where j.utilisateur.id = :utilisateurId and j.revoque = false")
    int revoquerTousPourUtilisateur(Long utilisateurId);

    /** Purge planifiée des jetons expirés. */
    @Modifying
    @Query("delete from JetonRafraichissement j where j.dateExpiration < :limite")
    int supprimerExpiresAvant(Instant limite);
}
