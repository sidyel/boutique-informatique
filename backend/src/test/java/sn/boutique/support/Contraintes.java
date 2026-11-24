package sn.boutique.support;

import org.hibernate.exception.ConstraintViolationException;

/**
 * Aide aux tests de contraintes PostgreSQL.
 */
public final class Contraintes {

    private Contraintes() {
    }

    /** Nom de la contrainte PostgreSQL violée, quelle que soit l'exception Spring qui l'enveloppe. */
    public static String contrainteViolee(Throwable erreur) {
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
