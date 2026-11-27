package sn.boutique.commande.paiement;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.commande.depot.PaiementDepot;
import sn.boutique.commande.modele.Paiement;
import sn.boutique.commande.modele.StatutPaiement;
import sn.boutique.commande.modele.TypeFournisseurPaiement;
import sn.boutique.commande.paiement.FournisseurPaiement.ResultatVerification;
import sn.boutique.noyau.alerte.AlerteAdminService;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Vérification d'un paiement auprès du prestataire (serveur à serveur), seule source de vérité du statut « payé ».
 * Appelée par la notification du prestataire, la page de retour du client et la tâche d'expiration des commandes.
 * <p>
 * <strong>L'appel au prestataire se fait hors de toute transaction</strong> : lecture courte du paiement, appel réseau,
 * puis enregistrement du résultat par {@link ReceptionPaiementService} dans sa propre transaction. Rien n'est verrouillé
 * pendant l'attente, et ce que dit le navigateur ou le corps d'une notification n'est jamais cru : seule la réponse du
 * prestataire compte, et son montant et son numéro de commande sont recontrôlés.
 */
@Slf4j
@Service
public class ConfirmationPaiementService {

    private static final String DEVISE = "XOF";

    private final List<FournisseurPaiement> fournisseurs;
    private final PaiementDepot paiementDepot;
    private final ReceptionPaiementService reception;
    private final AlerteAdminService alertes;
    private final TransactionTemplate transaction;

    public ConfirmationPaiementService(List<FournisseurPaiement> fournisseurs, PaiementDepot paiementDepot,
                                       ReceptionPaiementService reception, AlerteAdminService alertes,
                                       PlatformTransactionManager gestionnaireTransactions) {
        this.fournisseurs = fournisseurs;
        this.paiementDepot = paiementDepot;
        this.reception = reception;
        this.alertes = alertes;
        this.transaction = new TransactionTemplate(gestionnaireTransactions);
    }

    /** Issue d'une vérification. */
    public enum Resultat {
        /** Payé : la commande est réglée (ou réactivée), ou le paiement est à rembourser. */
        PAYE,
        /** Le prestataire n'a pas encore encaissé. */
        EN_ATTENTE,
        /** Échec ou annulation chez le prestataire. */
        ECHEC,
        /** Le prestataire annonce un paiement dont le montant ou la commande ne correspond pas : rien n'est enregistré. */
        INCOHERENT,
        /** Rien à vérifier (déjà traité, ou sans référence chez le prestataire). */
        SANS_OBJET
    }

    private record Lecture(TypeFournisseurPaiement fournisseur, String reference, long montant, String numero,
                           long totalCommande, StatutPaiement statut) {
    }

    /**
     * Vérifie un paiement. À appeler hors transaction.
     *
     * @throws PaiementIndisponibleException si le prestataire est injoignable (rien n'est modifié : l'appelant
     *                                       réessaiera plus tard)
     */
    public Resultat confirmer(long paiementId) {
        Lecture lecture = transaction.execute(statut -> paiementDepot.findById(paiementId)
                .map(p -> new Lecture(p.getFournisseur(), p.getReferenceExterne(), p.getMontant(),
                        p.getCommande().getNumero(), p.getCommande().getTotal(), p.getStatut()))
                .orElseThrow(() -> new RessourceIntrouvableException("Paiement", paiementId)));
        if (lecture.reference() == null || !verifiable(lecture.statut())) {
            return Resultat.SANS_OBJET;
        }
        FournisseurPaiement fournisseur = fournisseurs.stream()
                .filter(f -> f.type() == lecture.fournisseur() && f.disponible()).findFirst()
                .orElseThrow(() -> new PaiementIndisponibleException("Prestataire non configuré"));

        ResultatVerification verifie = fournisseur.verifier(lecture.reference());

        switch (verifie.statut()) {
            case EN_ATTENTE:
                return Resultat.EN_ATTENTE;
            case ECHOUE:
                reception.enregistrerEchec(paiementId, StatutPaiement.ECHOUE);
                return Resultat.ECHEC;
            case ANNULE:
                reception.enregistrerEchec(paiementId, StatutPaiement.ANNULE);
                return Resultat.ECHEC;
            default:
                break;
        }
        // REUSSI : le montant, la devise et le numéro de commande doivent correspondre à notre commande
        boolean coherent = verifie.montant() == lecture.montant() && verifie.montant() == lecture.totalCommande()
                && DEVISE.equals(verifie.devise())
                && (verifie.numeroCommande() == null || verifie.numeroCommande().equals(lecture.numero()));
        if (!coherent) {
            alertes.signaler("Paiement incohérent : commande " + lecture.numero(),
                    "Le prestataire annonce un paiement de %d %s (commande %s) pour la commande %s, attendue à %d FCFA. Rien n'a été enregistré."
                            .formatted(verifie.montant(), verifie.devise(), verifie.numeroCommande(), lecture.numero(),
                                    lecture.totalCommande()));
            return Resultat.INCOHERENT;
        }
        reception.enregistrerPaiementRecu(paiementId);
        return Resultat.PAYE;
    }

    /**
     * Vérifie tous les paiements en ligne d'une commande qui ne sont pas encore réglés (la facture en cours, mais
     * aussi les anciennes, que le client a pu payer quand même).
     *
     * @return {@code PAYE} si l'un d'eux est payé, sinon le résultat le plus avancé
     * @throws PaiementIndisponibleException si le prestataire est injoignable pour l'un d'eux et qu'aucun n'est payé
     */
    public Resultat confirmerCommande(String numero) {
        List<Long> ids = paiementDepot.idsAVerifier(numero, List.of(StatutPaiement.EN_ATTENTE, StatutPaiement.ANNULE));
        Resultat meilleur = Resultat.SANS_OBJET;
        PaiementIndisponibleException indisponible = null;
        for (long id : ids) {
            try {
                Resultat resultat = confirmer(id);
                if (resultat == Resultat.PAYE) {
                    return Resultat.PAYE;
                }
                if (meilleur == Resultat.SANS_OBJET || resultat == Resultat.EN_ATTENTE) {
                    meilleur = resultat;
                }
            } catch (PaiementIndisponibleException e) {
                indisponible = e;
            }
        }
        if (indisponible != null) {
            throw indisponible;
        }
        return meilleur;
    }

    /** Une facture en attente, ou abandonnée (annulée ou expirée chez nous) qui a pu être payée quand même. */
    private static boolean verifiable(StatutPaiement statut) {
        return statut == StatutPaiement.EN_ATTENTE || statut == StatutPaiement.ANNULE;
    }

    /** Pour la notification du prestataire : le paiement correspondant à une référence de facture. */
    public Optional<Long> idParReference(TypeFournisseurPaiement fournisseur, String reference) {
        return transaction.execute(statut -> paiementDepot.findByFournisseurAndReferenceExterne(fournisseur, reference)
                .map(Paiement::getId));
    }
}
