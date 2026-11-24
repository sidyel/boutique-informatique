package sn.boutique.commande.depot;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.commande.modele.Commande;

public interface CommandeDepot extends JpaRepository<Commande, Long> {

    Optional<Commande> findByNumero(String numero);

    Page<Commande> findByUtilisateurIdOrderByDateCreationDesc(Long utilisateurId, Pageable pageable);

    /** Prochaine valeur du compteur des numéros de commande (voir NumeroCommande). */
    @Query(value = "select nextval('sequence_numero_commande')", nativeQuery = true)
    long prochaineValeurNumero();
}
