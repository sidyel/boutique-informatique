package sn.boutique.noyau.erreur;

/**
 * Levée lorsqu'une règle métier n'est pas respectée (code promo expiré, montant minimum non atteint…).
 * Traduite en HTTP 422.
 */
public class RegleMetierException extends RuntimeException {

    private final String code;

    public RegleMetierException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Code stable et lisible par le frontend, en MAJUSCULES (ex. {@code CODE_PROMO_EXPIRE}). */
    public String getCode() {
        return code;
    }
}
