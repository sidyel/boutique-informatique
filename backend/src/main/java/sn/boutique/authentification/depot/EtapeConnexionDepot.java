package sn.boutique.authentification.depot;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.authentification.modele.EtapeConnexion;

public interface EtapeConnexionDepot extends JpaRepository<EtapeConnexion, Long> {

    /** Verrou de ligne : deux requêtes simultanées avec le même jeton d'étape sont traitées l'une après l'autre. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EtapeConnexion e join fetch e.utilisateur where e.empreinte = :empreinte")
    Optional<EtapeConnexion> trouverPourMiseAJour(String empreinte);

    /** Les étapes en cours d'un compte deviennent inutilisables (réinitialisation du second facteur, changement de rôle). */
    @Modifying(flushAutomatically = true)
    @Query("update EtapeConnexion e set e.dateUtilisation = :maintenant where e.utilisateur.id = :utilisateurId and e.dateUtilisation is null")
    int invaliderPourUtilisateur(Long utilisateurId, Instant maintenant);

    @Modifying
    @Query("delete from EtapeConnexion e where e.dateLimite < :limite")
    int supprimerExpireesAvant(Instant limite);
}
