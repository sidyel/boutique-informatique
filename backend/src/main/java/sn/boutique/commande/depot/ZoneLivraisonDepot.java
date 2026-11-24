package sn.boutique.commande.depot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.commande.modele.ZoneLivraison;

public interface ZoneLivraisonDepot extends JpaRepository<ZoneLivraison, Long> {

    List<ZoneLivraison> findByActifTrueOrderByTarifAsc();
}
