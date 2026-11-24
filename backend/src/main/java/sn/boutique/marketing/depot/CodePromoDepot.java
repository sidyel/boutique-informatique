package sn.boutique.marketing.depot;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.marketing.modele.CodePromo;

public interface CodePromoDepot extends JpaRepository<CodePromo, Long> {

    Optional<CodePromo> findByCode(String code);

    /** Recherche insensible à la casse et aux espaces de la saisie. */
    default Optional<CodePromo> trouverParCode(String saisie) {
        return findByCode(CodePromo.normaliserCode(saisie));
    }
}
