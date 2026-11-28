package sn.boutique.authentification.dto;

import sn.boutique.authentification.modele.EtapeConnexion.Etape;

/**
 * Réponse de la connexion d'un compte interne : le mot de passe est bon, il reste une étape. Le jeton d'étape est opaque,
 * n'ouvre aucune route et expire vite (ADR 0009 §1).
 */
public record EtapeConnexionReponse(Etape etape, String jetonEtape) {
}
