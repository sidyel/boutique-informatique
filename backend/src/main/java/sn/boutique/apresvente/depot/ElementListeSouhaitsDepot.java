package sn.boutique.apresvente.depot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.apresvente.modele.ElementListeSouhaits;

public interface ElementListeSouhaitsDepot extends JpaRepository<ElementListeSouhaits, Long> {

    List<ElementListeSouhaits> findByUtilisateurIdOrderByDateCreationDesc(Long utilisateurId);

    boolean existsByUtilisateurIdAndVarianteId(Long utilisateurId, Long varianteId);
}
