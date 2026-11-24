package sn.boutique.authentification.service;

/** Lien reçu par e-mail inconnu, expiré ou déjà utilisé : même réponse dans tous les cas. */
public class JetonInvalideException extends RuntimeException {

    public JetonInvalideException(String raisonJournalisee) {
        super(raisonJournalisee);
    }
}
