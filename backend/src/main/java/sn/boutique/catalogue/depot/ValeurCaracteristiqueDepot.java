package sn.boutique.catalogue.depot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.catalogue.modele.ValeurCaracteristique;

public interface ValeurCaracteristiqueDepot extends JpaRepository<ValeurCaracteristique, Long> {

    /** Valeurs d'un produit et de ses variantes, avec leur définition (tableau de caractéristiques). */
    @Query("select v from ValeurCaracteristique v join fetch v.definition where v.produit.id = :produitId")
    List<ValeurCaracteristique> trouverParProduit(Long produitId);
}
