package sn.boutique.commande.modele;

/**
 * Cycle de vie d'une commande.
 */
public enum StatutCommande {
    /** Paiement en ligne non encore confirmé par le prestataire : le stock est réservé jusqu'à {@code expireLe}. */
    EN_ATTENTE_PAIEMENT,
    /** Paiement à la livraison : en attente de l'appel du service client (stock réservé jusqu'à {@code expireLe}). */
    EN_ATTENTE_CONFIRMATION,
    /** Paiement à la livraison : commande validée, pas encore payée. */
    CONFIRMEE,
    PAYEE,
    EN_PREPARATION,
    EXPEDIEE,
    LIVREE,
    ANNULEE,
    REMBOURSEE
}
