package sn.boutique.noyau.telephone;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidateurTelephoneSenegalais implements ConstraintValidator<TelephoneSenegalais, String> {

    @Override
    public boolean isValid(String valeur, ConstraintValidatorContext contexte) {
        return valeur == null || valeur.isBlank() || TelephoneSenegal.estValide(valeur);
    }
}
