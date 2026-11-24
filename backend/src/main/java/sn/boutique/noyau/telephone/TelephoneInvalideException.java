package sn.boutique.noyau.telephone;

import sn.boutique.noyau.erreur.RegleMetierException;

/**
 * Numéro de téléphone qui n'est pas un numéro sénégalais valide. Traduite en HTTP 422.
 * Les DTO utilisent plutôt {@link TelephoneSenegalais} (erreur de validation 400 par champ).
 */
public class TelephoneInvalideException extends RegleMetierException {

    public TelephoneInvalideException(String saisie) {
        super("TELEPHONE_INVALIDE",
                "Le numéro « %s » n'est pas un numéro sénégalais valide.".formatted(saisie));
    }
}
