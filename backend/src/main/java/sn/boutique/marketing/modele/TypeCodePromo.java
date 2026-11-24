package sn.boutique.marketing.modele;

/**
 * Nature de la réduction d'un code promo.
 */
public enum TypeCodePromo {
    /** valeur = pourcentage de 1 à 100 du sous-total éligible. */
    POURCENTAGE,
    /** valeur = montant en FCFA. */
    MONTANT_FIXE,
    /** valeur ignorée (0) : frais de livraison offerts. */
    LIVRAISON_OFFERTE
}
