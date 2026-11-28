package sn.boutique.administration.securite;

/** Action sensible sans fenêtre de réauthentification ouverte : 403 {@code REAUTHENTIFICATION_REQUISE}. */
public class ReauthentificationRequiseException extends RuntimeException {

    public ReauthentificationRequiseException(String raisonJournalisee) {
        super(raisonJournalisee);
    }
}
