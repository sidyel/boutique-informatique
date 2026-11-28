package sn.boutique.administration.securite;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Action d'administration qui exige un code TOTP saisi dans les dix dernières minutes (ADR 0009 §8) : gestion des
 * utilisateurs, avoirs, mentions de facturation, marquage « remboursé », exports de données clients. Sans cette fenêtre, la
 * route répond 403 {@code REAUTHENTIFICATION_REQUISE} et l'écran demande le code puis rejoue la requête.
 * <p>
 * Le test {@code MatriceAdministrationTest} recense les routes marquées : en ajouter ou en retirer une est un choix visible.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ReauthentificationRequise {
}
