package sn.boutique.stock.modele;

/**
 * Origine d'un mouvement de stock.
 */
public enum TypeMouvementStock {
    ENTREE,
    SORTIE_VENTE,
    AJUSTEMENT,
    RETOUR,
    /** Libération du stock réservé par une commande annulée ou expirée. */
    ANNULATION
}
