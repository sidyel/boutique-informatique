package sn.boutique.catalogue.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Texte alternatif d'une image (à combiner avec {@code @NotNull}) : de 5 à 150 caractères (espaces de bord ignorés), et qui ne
 * ressemble pas à un nom de fichier (« IMG_1234.jpg », « DSC0001 », « photo.png »). Il est lu par les
 * lecteurs d'écran et par Google Images : il doit décrire l'image.
 */
@Documented
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, TYPE_USE})
@Constraint(validatedBy = TexteAlternatifValidateur.class)
public @interface TexteAlternatif {

    String message() default "Le texte alternatif doit décrire l'image en 5 à 150 caractères (pas un nom de fichier).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
