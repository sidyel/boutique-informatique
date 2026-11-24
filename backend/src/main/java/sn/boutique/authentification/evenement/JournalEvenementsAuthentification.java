package sn.boutique.authentification.evenement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * PROVISOIRE (commit 6) : journalise les événements d'inscription. Le commit 7 le remplace par
 * l'envoi des e-mails (vérification de l'adresse, « vous avez déjà un compte ») via Mailpit en dev.
 * Les écouteurs ne s'exécutent qu'après validation de la transaction.
 */
@Slf4j
@Component
class JournalEvenementsAuthentification {

    @TransactionalEventListener
    void surInscription(InscriptionEffectuee evenement) {
        log.info("Compte {} créé : e-mail de vérification à envoyer", evenement.utilisateurId());
    }

    @TransactionalEventListener
    void surInscriptionAvecEmailExistant(InscriptionAvecEmailExistant evenement) {
        log.info("Compte {} : e-mail « vous avez déjà un compte » à envoyer", evenement.utilisateurId());
    }
}
