package sn.boutique.marketing.depot;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.marketing.modele.JournalConversion;
import sn.boutique.marketing.modele.PlateformeConversion;

public interface JournalConversionDepot extends JpaRepository<JournalConversion, Long> {

    boolean existsByPlateformeAndIdEvenement(PlateformeConversion plateforme, String idEvenement);
}
