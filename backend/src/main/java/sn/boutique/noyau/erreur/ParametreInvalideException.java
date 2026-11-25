package sn.boutique.noyau.erreur;

import lombok.Getter;

/**
 * Paramètre de requête invalide (filtre inconnu, nombre mal formé, page hors limites…).
 * Traduite en HTTP 400, code {@code PARAMETRE_INVALIDE}, avec le nom du paramètre.
 */
@Getter
public class ParametreInvalideException extends RuntimeException {

    private final String parametre;

    public ParametreInvalideException(String parametre, String message) {
        super(message);
        this.parametre = parametre;
    }
}
