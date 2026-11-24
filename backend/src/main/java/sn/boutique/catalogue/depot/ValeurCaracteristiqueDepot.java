package sn.boutique.catalogue.depot;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.catalogue.modele.ValeurCaracteristique;

public interface ValeurCaracteristiqueDepot extends JpaRepository<ValeurCaracteristique, Long> {
}
