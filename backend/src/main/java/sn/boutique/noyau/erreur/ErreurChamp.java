package sn.boutique.noyau.erreur;

/**
 * Détail d'une erreur de validation, renvoyé dans la propriété {@code erreurs} du ProblemDetail.
 *
 * @param champ   nom du champ en erreur (ex. {@code email}, {@code lignes[0].quantite})
 * @param message message lisible en français
 */
public record ErreurChamp(String champ, String message) {
}
