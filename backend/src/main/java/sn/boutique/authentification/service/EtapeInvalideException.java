package sn.boutique.authentification.service;

/** Jeton d'étape inconnu, expiré, épuisé (trop de codes faux) ou présenté à la mauvaise étape : recommencer par le mot de passe. */
public class EtapeInvalideException extends RuntimeException {

    public EtapeInvalideException(String raisonJournalisee) {
        super(raisonJournalisee);
    }
}
