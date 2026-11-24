package sn.boutique.noyau.telephone;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Valide qu'un champ de requête est un numéro sénégalais (mobile 70/75/76/77/78 ou fixe 33),
 * quel que soit son format de saisie. Une valeur nulle est acceptée : combiner avec
 * {@code @NotBlank} si le champ est obligatoire. La normalisation E.164 est faite par l'entité.
 */
@Documented
@Constraint(validatedBy = ValidateurTelephoneSenegalais.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface TelephoneSenegalais {

    String message() default "Numéro de téléphone sénégalais invalide (ex. 77 123 45 67)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
