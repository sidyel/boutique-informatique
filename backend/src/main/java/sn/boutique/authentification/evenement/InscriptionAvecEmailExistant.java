package sn.boutique.authentification.evenement;

/**
 * Inscription demandée avec l'e-mail d'un compte existant : le titulaire recevra un e-mail
 * « vous avez déjà un compte » (avec un lien vers la connexion et le mot de passe oublié).
 */
public record InscriptionAvecEmailExistant(Long utilisateurId) {
}
