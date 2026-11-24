package sn.boutique.authentification.evenement;

/** Renvoi de l'e-mail de vérification demandé pour un compte existant non vérifié. */
public record RenvoiVerificationDemande(Long utilisateurId) {
}
