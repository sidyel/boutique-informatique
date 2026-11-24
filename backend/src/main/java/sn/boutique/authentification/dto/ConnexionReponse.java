package sn.boutique.authentification.dto;

import sn.boutique.compte.dto.ProfilReponse;

/**
 * Réponse de la connexion et du rafraîchissement. Le jeton de rafraîchissement n'y figure pas :
 * il est uniquement dans le cookie HttpOnly.
 *
 * @param jetonAcces JWT à envoyer dans l'en-tête {@code Authorization: Bearer …}
 * @param expireDans durée de validité du jeton d'accès, en secondes
 */
public record ConnexionReponse(String jetonAcces, String typeJeton, long expireDans, ProfilReponse utilisateur) {
}
