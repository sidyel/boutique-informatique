package sn.boutique.commande.modele;

/**
 * Cycle de vie d'une commande.
 */
public enum StatutCommande {
    EN_ATTENTE_PAIEMENT,
    /** Paiement à la livraison : commande validée, pas encore payée. */
    CONFIRMEE,
    PAYEE,
    EN_PREPARATION,
    EXPEDIEE,
    LIVREE,
    ANNULEE,
    REMBOURSEE
}
