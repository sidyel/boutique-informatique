package sn.boutique.authentification.service;

/** Code TOTP ou code de secours faux, déjà utilisé ou mal formé. Réponse 401 générique : rien n'indique pourquoi. */
public class CodeSecondFacteurInvalideException extends RuntimeException {

    public CodeSecondFacteurInvalideException(String raisonJournalisee) {
        super(raisonJournalisee);
    }
}
