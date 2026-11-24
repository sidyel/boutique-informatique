package sn.boutique.apresvente.depot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.apresvente.modele.DemandeSav;

public interface DemandeSavDepot extends JpaRepository<DemandeSav, Long> {

    List<DemandeSav> findByCommandeIdOrderByDateCreationDesc(Long commandeId);
}
