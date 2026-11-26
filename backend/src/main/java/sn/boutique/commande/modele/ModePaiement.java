package sn.boutique.commande.modele;

/**
 * Moyen de paiement choisi par le client.
 */
public enum ModePaiement {
    /** Wave, Orange Money ou Free Money : le client choisit sur la page du prestataire de paiement. */
    MOBILE_MONEY,
    CARTE_BANCAIRE,
    /** Payé en espèces ou en mobile money au livreur (ou en boutique) ; confirmé par téléphone d'abord. */
    A_LA_LIVRAISON,
    /** Anciens modes, gardés pour les commandes déjà enregistrées : le tunnel ne les propose plus. */
    WAVE,
    ORANGE_MONEY,
    FREE_MONEY;

    /** Paiement en ligne : redirection vers la page du prestataire. */
    public boolean enLigne() {
        return this == MOBILE_MONEY || this == CARTE_BANCAIRE;
    }
}
