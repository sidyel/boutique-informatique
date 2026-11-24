package sn.boutique.commande.depot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.commande.modele.LigneCommande;

public interface LigneCommandeDepot extends JpaRepository<LigneCommande, Long> {

    boolean existsByVarianteId(Long varianteId);

    /**
     * Vrai si au moins une variante du produit a été commandée. La suppression d'un produit dans
     * l'admin (phase 6) devient alors une désactivation (actif = false) : voir ADR 0004.
     */
    @Query("select count(l) > 0 from LigneCommande l where l.variante.produit.id = :produitId")
    boolean produitDejaCommande(Long produitId);
}
