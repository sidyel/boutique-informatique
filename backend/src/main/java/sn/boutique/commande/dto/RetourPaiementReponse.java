package sn.boutique.commande.dto;

/**
 * Ce que la page de retour du prestataire de paiement peut afficher : rien de plus que le statut.
 *
 * @param numero  numéro de commande
 * @param statut  {@code PAYE}, {@code EN_ATTENTE} (paiement pas encore confirmé), {@code ECHEC} (échec ou annulation),
 *                {@code A_REMBOURSER} (paiement reçu mais commande impossible) ou {@code INCONNU}
 * @param verifie faux si le prestataire n'a pas pu être interrogé (la page réessaie)
 */
public record RetourPaiementReponse(String numero, String statut, boolean verifie) {
}
