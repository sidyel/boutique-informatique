package sn.boutique.authentification.depot;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.authentification.modele.JetonRafraichissement;

public interface JetonRafraichissementDepot extends JpaRepository<JetonRafraichissement, Long> {

    Optional<JetonRafraichissement> findByEmpreinte(String empreinte);

    /**
     * Lecture avec verrou de ligne (SELECT … FOR UPDATE) : deux rafraîchissements simultanés du même jeton
     * sont sérialisés, le second voit le jeton déjà utilisé.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JetonRafraichissement j join fetch j.utilisateur where j.empreinte = :empreinte")
    Optional<JetonRafraichissement> trouverPourRotation(String empreinte);

    /** Réutilisation détectée ou déconnexion : toute la famille devient inutilisable. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JetonRafraichissement j set j.revoque = true where j.famille = :famille and j.revoque = false")
    int revoquerFamille(UUID famille);

    /** « Se déconnecter de tous les appareils », changement de mot de passe. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JetonRafraichissement j set j.revoque = true where j.utilisateur.id = :utilisateurId and j.revoque = false")
    int revoquerTousPourUtilisateur(Long utilisateurId);

    /** Changement de mot de passe : toutes les sessions sauf celle de l'appareil qui vient de le changer. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JetonRafraichissement j set j.revoque = true where j.utilisateur.id = :utilisateurId and j.famille <> :famille and j.revoque = false")
    int revoquerAutresFamilles(Long utilisateurId, UUID famille);

    /** Purge planifiée des jetons expirés. */
    @Modifying
    @Query("delete from JetonRafraichissement j where j.dateExpiration < :limite")
    int supprimerExpiresAvant(Instant limite);
}
