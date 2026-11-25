package sn.boutique.authentification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import sn.boutique.authentification.evenement.CompteVerrouille;
import sn.boutique.authentification.evenement.InscriptionAvecEmailExistant;
import sn.boutique.authentification.evenement.InscriptionEffectuee;
import sn.boutique.authentification.evenement.MotDePasseModifie;
import sn.boutique.authentification.evenement.ReinitialisationDemandee;
import sn.boutique.authentification.evenement.RenvoiVerificationDemande;

/**
 * Envoie les e-mails du compte <strong>après</strong> validation de la transaction (un rollback n'envoie
 * rien) et <strong>en arrière-plan</strong> : la réponse HTTP part tout de suite, avec la même durée
 * que l'e-mail existe ou non (anti-énumération).
 */
@Component
@RequiredArgsConstructor
class EnvoiEmailsCompte {

    private final EmailsCompteService emails;

    @Async
    @TransactionalEventListener
    void surInscription(InscriptionEffectuee evenement) {
        emails.envoyerVerification(evenement.utilisateurId());
    }

    @Async
    @TransactionalEventListener
    void surRenvoiVerification(RenvoiVerificationDemande evenement) {
        emails.envoyerVerification(evenement.utilisateurId());
    }

    @Async
    @TransactionalEventListener
    void surInscriptionAvecEmailExistant(InscriptionAvecEmailExistant evenement) {
        emails.envoyerCompteExistant(evenement.utilisateurId());
    }

    @Async
    @TransactionalEventListener
    void surReinitialisationDemandee(ReinitialisationDemandee evenement) {
        emails.envoyerReinitialisation(evenement.utilisateurId());
    }

    /** fallbackExecution : publié pendant une connexion échouée, dont la transaction est validée malgré l'erreur. */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    void surCompteVerrouille(CompteVerrouille evenement) {
        emails.envoyerDeverrouillage(evenement.utilisateurId());
    }

    @Async
    @TransactionalEventListener
    void surMotDePasseModifie(MotDePasseModifie evenement) {
        emails.envoyerMotDePasseModifie(evenement.utilisateurId());
    }
}
