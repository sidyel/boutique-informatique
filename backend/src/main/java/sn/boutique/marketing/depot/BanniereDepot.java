package sn.boutique.marketing.depot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.marketing.modele.Banniere;
import sn.boutique.marketing.modele.EmplacementBanniere;

public interface BanniereDepot extends JpaRepository<Banniere, Long> {

    /** Candidates d'un emplacement ; la période (dateDebut, dateFin) est filtrée par le service. */
    List<Banniere> findByEmplacementAndActifTrueOrderByPositionAsc(EmplacementBanniere emplacement);
}
