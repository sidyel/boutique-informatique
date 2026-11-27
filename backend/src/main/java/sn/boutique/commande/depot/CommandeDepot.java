package sn.boutique.commande.depot;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.StatutCommande;

public interface CommandeDepot extends JpaRepository<Commande, Long> {

    Optional<Commande> findByNumero(String numero);

    Page<Commande> findByUtilisateurIdOrderByDateCreationDesc(Long utilisateurId, Pageable pageable);

    /** Utilisations d'un code promo par un client connecté (commandes non annulées). */
    long countByUtilisateurIdAndCodePromoAndStatutNot(Long utilisateurId, String codePromo, StatutCommande statut);

    /** Page de suivi : le jeton du lien est cherché par son empreinte SHA-256. */
    Optional<Commande> findByEmpreinteJetonSuivi(String empreinte);

    /** Même chose, avec verrou : annulation, confirmation et expiration ne s'exécutent jamais en double. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Commande c where c.id = :id")
    Optional<Commande> findByIdPourMiseAJour(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Commande c where c.numero = :numero")
    Optional<Commande> findByNumeroPourMiseAJour(String numero);

    /** Commandes à la livraison encore à confirmer pour ce numéro (plafond configurable). */
    long countByTelephoneClientAndStatut(String telephoneClient, StatutCommande statut);

    /** Utilisations d'un code promo par un invité, identifié par son numéro de téléphone. */
    long countByTelephoneClientAndCodePromoAndStatutNot(String telephoneClient, String codePromo,
                                                        StatutCommande statut);

    Page<Commande> findByStatutOrderByDateCreationAsc(StatutCommande statut, Pageable pageable);

    /** Prochaine valeur du compteur des numéros de commande (voir NumeroCommande). */
    @Query(value = "select nextval('sequence_numero_commande')", nativeQuery = true)
    long prochaineValeurNumero();
}
