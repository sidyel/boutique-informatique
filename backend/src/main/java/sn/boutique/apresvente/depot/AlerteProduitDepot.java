package sn.boutique.apresvente.depot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.apresvente.modele.AlerteProduit;
import sn.boutique.apresvente.modele.TypeAlerte;

public interface AlerteProduitDepot extends JpaRepository<AlerteProduit, Long> {

    List<AlerteProduit> findByVarianteIdAndTypeAndEnvoyeeFalse(Long varianteId, TypeAlerte type);
}
