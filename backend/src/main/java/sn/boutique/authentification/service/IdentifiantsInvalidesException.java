package sn.boutique.authentification.service;

/**
 * Échec de connexion. Même réponse 401 que l'e-mail soit inconnu, le mot de passe faux, le compte
 * désactivé ou verrouillé : la réponse ne révèle pas quelles adresses ont un compte (voir ADR 0003).
 */
public class IdentifiantsInvalidesException extends RuntimeException {

    public IdentifiantsInvalidesException(String raisonJournalisee) {
        super(raisonJournalisee);
    }
}
