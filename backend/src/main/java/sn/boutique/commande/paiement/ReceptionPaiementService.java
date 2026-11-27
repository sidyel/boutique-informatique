package sn.boutique.commande.paiement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.depot.PaiementDepot;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.Paiement;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.commande.modele.StatutPaiement;
import sn.boutique.noyau.alerte.AlerteAdminService;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Enregistrement d'un paiement <strong>déjà vérifié auprès du prestataire</strong> (le webhook de la phase 4c
 * appelle cette méthode après sa vérification serveur à serveur ; rien d'autre ne marque une commande payée).
 * <p>
 * La commande est verrouillée : deux paiements reçus en même temps pour la même commande passent l'un après
 * l'autre. Le premier règle la commande ; tout paiement suivant est <strong>marqué « à rembourser »</strong> et
 * signalé à l'équipe, sans jamais toucher à la commande déjà réglée.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceptionPaiementService {

    public static final String AUTEUR_PAIEMENT = "PAIEMENT";

    private final CommandeDepot commandeDepot;
    private final PaiementDepot paiementDepot;
    private final AlerteAdminService alertes;

    /** Issue d'un paiement reçu. */
    public enum Issue {
        /** La commande est réglée par ce paiement. */
        COMMANDE_REGLEE,
        /** Paiement sans vente à laquelle le rattacher : à rembourser. */
        A_REMBOURSER,
        /** Paiement déjà enregistré (webhook reçu deux fois) : rien à faire. */
        DEJA_ENREGISTRE
    }

    @Transactional
    public Issue enregistrerPaiementRecu(long paiementId) {
        String numero = paiementDepot.numeroCommande(paiementId)
                .orElseThrow(() -> new RessourceIntrouvableException("Paiement", paiementId));
        Commande commande = commandeDepot.findByNumeroPourMiseAJour(numero)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", numero));
        // Le paiement n'est lu qu'une fois la commande verrouillée : un webhook concurrent l'a peut-être déjà traité
        Paiement paiement = paiementDepot.findById(paiementId)
                .orElseThrow(() -> new RessourceIntrouvableException("Paiement", paiementId));
        if (paiement.getStatut() == StatutPaiement.REUSSI || paiement.getStatut() == StatutPaiement.A_REMBOURSER
                || paiement.getStatut() == StatutPaiement.REMBOURSE) {
            return Issue.DEJA_ENREGISTRE;
        }
        if (commande.getStatut() == StatutCommande.EN_ATTENTE_PAIEMENT) {
            paiement.setStatut(StatutPaiement.REUSSI);
            commande.changerStatut(StatutCommande.PAYEE, AUTEUR_PAIEMENT, "Paiement reçu");
            commande.setExpireLe(null);
            log.info("Commande {} réglée", commande.getNumero());
            return Issue.COMMANDE_REGLEE;
        }
        // Commande déjà réglée (seconde facture payée), annulée ou expirée : l'argent est là sans vente.
        // La réactivation d'une commande expirée dont le stock est encore disponible est traitée en 4c.
        paiement.setStatut(StatutPaiement.A_REMBOURSER);
        alertes.signaler("Paiement à rembourser : commande " + commande.getNumero(),
                "Un paiement de %d FCFA (référence %s) est arrivé pour la commande %s, dont le statut est %s. Il est marqué « à rembourser »."
                        .formatted(paiement.getMontant(), paiement.getReferenceExterne(), commande.getNumero(),
                                commande.getStatut()));
        return Issue.A_REMBOURSER;
    }
}
