package sn.boutique.contenu.depot;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.contenu.modele.ParametresSite;

public interface ParametresSiteDepot extends JpaRepository<ParametresSite, Long> {

    /** La ligne unique, créée par la migration V5. */
    default ParametresSite lire() {
        return findById(ParametresSite.ID_UNIQUE)
                .orElseThrow(() -> new IllegalStateException("Ligne parametres_site absente (migration V5)"));
    }
}
