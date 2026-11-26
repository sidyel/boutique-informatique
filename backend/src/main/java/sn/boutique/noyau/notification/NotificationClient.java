package sn.boutique.noyau.notification;

/**
 * Envoi d'un message court au client (WhatsApp, SMS). Interface seulement pour l'instant : aucun prestataire
 * n'est branché (voir {@link NotificationJournal}). Le contenu ne doit contenir aucun secret ; le lien de suivi
 * d'une commande en est un : il n'est jamais écrit dans les journaux.
 */
public interface NotificationClient {

    /**
     * @param telephone numéro E.164 (+221…)
     * @param message   texte à envoyer
     */
    void envoyer(String telephone, String message);
}
