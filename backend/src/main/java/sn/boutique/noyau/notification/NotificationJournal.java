package sn.boutique.noyau.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implémentation par défaut : n'envoie rien, journalise seulement l'intention (numéro masqué, longueur du
 * message). Pour brancher un vrai prestataire : le déclarer {@code @Primary}.
 */
@Slf4j
@Component
public class NotificationJournal implements NotificationClient {

    @Override
    public void envoyer(String telephone, String message) {
        log.info("Notification client non envoyée (aucun prestataire WhatsApp/SMS branché) : numéro {}, {} caractères",
                masquer(telephone), message.length());
    }

    static String masquer(String telephone) {
        if (telephone == null || telephone.length() < 6) {
            return "***";
        }
        return telephone.substring(0, 4) + "•••" + telephone.substring(telephone.length() - 2);
    }
}
