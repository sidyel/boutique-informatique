package sn.boutique.commande.modele;

/**
 * Prestataire qui a traité un paiement. (Le nom FournisseurPaiement est réservé à l'interface des agrégateurs, phase 4.)
 */
public enum TypeFournisseurPaiement {
    STRIPE,
    PAYDUNYA,
    CINETPAY,
    WAVE,
    ORANGE_MONEY,
    A_LA_LIVRAISON
}
