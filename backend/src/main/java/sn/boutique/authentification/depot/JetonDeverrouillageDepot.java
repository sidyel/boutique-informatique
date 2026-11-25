package sn.boutique.authentification.depot;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.authentification.modele.JetonDeverrouillage;

public interface JetonDeverrouillageDepot extends JpaRepository<JetonDeverrouillage, Long> {

    Optional<JetonDeverrouillage> findByEmpreinte(String empreinte);

    /** Un lien encore valable existe-t-il ? Alors pas de nouvel e-mail (pas d'avalanche pour la victime). */
    @Query("select count(j) > 0 from JetonDeverrouillage j where j.utilisateur.id = :utilisateurId "
            + "and j.dateUtilisation is null and j.dateExpiration > :maintenant")
    boolean lienValableExiste(Long utilisateurId, Instant maintenant);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JetonDeverrouillage j set j.dateUtilisation = :maintenant where j.utilisateur.id = :utilisateurId and j.dateUtilisation is null")
    int invaliderPourUtilisateur(Long utilisateurId, Instant maintenant);

    @Modifying
    @Query("delete from JetonDeverrouillage j where j.dateExpiration < :limite")
    int supprimerExpiresAvant(Instant limite);
}
