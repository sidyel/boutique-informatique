package sn.boutique.catalogue.depot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.catalogue.modele.DefinitionCaracteristique;

public interface DefinitionCaracteristiqueDepot extends JpaRepository<DefinitionCaracteristique, Long> {

    List<DefinitionCaracteristique> findByCategorieIdOrderByGroupeAscOrdreAsc(Long categorieId);
}
