package sn.boutique.authentification.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MotDePasseRobusteValidateur implements ConstraintValidator<MotDePasseRobuste, String> {

    static final int CARACTERES_MIN = 10;
    static final int OCTETS_MAX = 72;

    @Override
    public boolean isValid(String valeur, ConstraintValidatorContext contexte) {
        if (valeur == null) {
            return true;
        }
        return valeur.codePointCount(0, valeur.length()) >= CARACTERES_MIN
                && valeur.getBytes(StandardCharsets.UTF_8).length <= OCTETS_MAX;
    }
}
