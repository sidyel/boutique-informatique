package sn.boutique.commande.dto;

/**
 * Ce que le tunnel de commande peut proposer aujourd'hui (réglages du site, prestataire de paiement configuré).
 *
 * @param paiementEnLigne        mobile money et carte disponibles (prestataire configuré)
 * @param paiementLivraison      paiement à la livraison ou au retrait proposé
 * @param paiementLivraisonMax   montant maximal (FCFA) d'une commande à la livraison
 * @param retraitMagasin         boutique physique configurée : retrait possible
 * @param boutiqueAdresse        adresse de retrait affichée dans le tunnel (null sans boutique)
 * @param delaiPaiementMinutes   durée de réservation du stock pour un paiement en ligne
 */
public record OptionsCommandeReponse(
        boolean paiementEnLigne,
        boolean paiementLivraison,
        long paiementLivraisonMax,
        boolean retraitMagasin,
        String boutiqueAdresse,
        int delaiPaiementMinutes) {
}
