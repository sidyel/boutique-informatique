package sn.boutique.authentification.depot;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.authentification.modele.CodeSecours;

public interface CodeSecoursDepot extends JpaRepository<CodeSecours, Long> {

    @Query("select count(c) from CodeSecours c where c.utilisateur.id = :utilisateurId and c.dateUtilisation is null")
    long compterRestants(Long utilisateurId);

    /** Consomme un code de secours : 1 si le code existe, appartient au compte et n'avait pas servi (atomique). */
    @Modifying(flushAutomatically = true)
    @Query("update CodeSecours c set c.dateUtilisation = :maintenant "
            + "where c.empreinte = :empreinte and c.utilisateur.id = :utilisateurId and c.dateUtilisation is null")
    int consommer(Long utilisateurId, String empreinte, Instant maintenant);

    @Modifying(flushAutomatically = true)
    @Query("delete from CodeSecours c where c.utilisateur.id = :utilisateurId")
    int supprimerPourUtilisateur(Long utilisateurId);
}
