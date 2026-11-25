package sn.boutique.authentification.evenement;

/** Un couple (e-mail, IP) de ce compte vient d'être verrouillé : le titulaire reçoit un lien de déverrouillage. */
public record CompteVerrouille(Long utilisateurId) {
}
