package sn.boutique.commande.modele;

/**
 * État d'un paiement, fixé uniquement par le webhook vérifié du prestataire.
 */
public enum StatutPaiement {
    EN_ATTENTE,
    REUSSI,
    ECHOUE,
    ANNULE,
    REMBOURSE
}
