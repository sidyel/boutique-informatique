package sn.boutique.commande.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.commande.depot.PaiementDepot;
import sn.boutique.commande.dto.RetourPaiementReponse;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.commande.paiement.ConfirmationPaiementService;
import sn.boutique.commande.paiement.ConfirmationPaiementService.Resultat;
import sn.boutique.commande.paiement.PaiementIndisponibleException;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.modele.StatutPaiement;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Retour du client depuis la page de paiement du prestataire. Sans transaction : la vérification appelle le
 * prestataire (voir {@link ConfirmationPaiementService}), puis le statut est relu.
 */
@Slf4j
@Service
public class RetourPaiementService {

    private final PaiementDepot paiementDepot;
    private final CommandeDepot commandeDepot;
    private final ConfirmationPaiementService confirmation;
    private final TransactionTemplate transaction;

    public RetourPaiementService(PaiementDepot paiementDepot, CommandeDepot commandeDepot,
                                 ConfirmationPaiementService confirmation,
                                 PlatformTransactionManager gestionnaireTransactions) {
        this.paiementDepot = paiementDepot;
        this.commandeDepot = commandeDepot;
        this.confirmation = confirmation;
        this.transaction = new TransactionTemplate(gestionnaireTransactions);
        this.transaction.setReadOnly(true);
    }

    public RetourPaiementReponse lire(String jeton) {
        String numero = paiementDepot.numeroParEmpreinteRetour(GenerateurJetons.empreinte(jeton))
                .orElseThrow(() -> new RessourceIntrouvableException("Retour de paiement", "inconnu"));
        boolean verifie = true;
        Resultat resultat = null;
        try {
            resultat = confirmation.confirmerCommande(numero);
        } catch (PaiementIndisponibleException e) {
            verifie = false;
            log.warn("Retour de paiement de la commande {} : prestataire injoignable", numero);
        }
        var lu = statut(numero, resultat);
        return new RetourPaiementReponse(numero, lu.statut(), lu.expireLe(), verifie);
    }

    private record Lu(String statut, java.time.Instant expireLe) {
    }

    private Lu statut(String numero, Resultat resultat) {
        return transaction.execute(t -> {
            var commande = commandeDepot.findByNumero(numero).orElseThrow();
            return new Lu(statutLu(commande, resultat), commande.getStatut() == StatutCommande.EN_ATTENTE_PAIEMENT
                    ? commande.getExpireLe() : null);
        });
    }

    private String statutLu(sn.boutique.commande.modele.Commande commande, Resultat resultat) {
        if (commande.getPaiements().stream().anyMatch(p -> p.getStatut() == StatutPaiement.A_REMBOURSER)) {
            return "A_REMBOURSER";
        }
        if (commande.getStatut() != StatutCommande.EN_ATTENTE_PAIEMENT && commande.getStatut() != StatutCommande.ANNULEE) {
            return "PAYE";
        }
        if (resultat == Resultat.ECHEC) {
            return "ECHEC";
        }
        return commande.getStatut() == StatutCommande.ANNULEE ? "ECHEC" : "EN_ATTENTE";
    }
}
