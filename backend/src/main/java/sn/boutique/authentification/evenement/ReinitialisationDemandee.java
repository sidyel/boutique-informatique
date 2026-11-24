package sn.boutique.authentification.evenement;

/** Mot de passe oublié demandé pour un compte existant et actif : le lien sera envoyé par e-mail. */
public record ReinitialisationDemandee(Long utilisateurId) {
}
