package sn.boutique.administration.securite;

/** Jeton d'accès encore valide, mais le compte a été désactivé ou son rôle a changé depuis son émission. */
public class SessionAdministrationInvalideException extends RuntimeException {

    public SessionAdministrationInvalideException(String raisonJournalisee) {
        super(raisonJournalisee);
    }
}
