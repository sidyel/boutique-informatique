package sn.boutique.authentification.service;

/**
 * Jeton de rafraîchissement absent, inconnu, expiré, révoqué ou réutilisé.
 * Le client reçoit toujours la même réponse 401 : la raison exacte n'est que journalisée.
 */
public class SessionInvalideException extends RuntimeException {

    public SessionInvalideException(String raisonJournalisee) {
        super(raisonJournalisee);
    }
}
