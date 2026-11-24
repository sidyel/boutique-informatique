package sn.boutique.authentification.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Mot de passe choisi par l'utilisateur : 10 caractères minimum et 72 octets maximum (limite de BCrypt).
 * Pas d'exigence de majuscule ou de caractère spécial (recommandation NIST 800-63B : la longueur compte
 * davantage). Une valeur nulle est acceptée : combiner avec {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = MotDePasseRobusteValidateur.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MotDePasseRobuste {

    String message() default "Le mot de passe doit contenir au moins 10 caractères (72 octets au maximum)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
