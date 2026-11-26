package sn.boutique.panier.dto;

import java.util.List;

/**
 * Panier tel que le client le voit. Tous les montants (FCFA) sont recalculés par le serveur à chaque lecture.
 *
 * @param nombreArticles somme des quantités (icône du panier)
 * @param sousTotal      somme des lignes commandables
 * @param remise         remise du code promo (0 si aucun code ou code invalide)
 * @param fraisLivraison frais de la zone demandée ; null si aucune zone n'est choisie
 * @param total          sous-total − remise + frais de livraison connus
 * @param prixModifies   au moins une ligne a changé de prix depuis que le client l'a vue : à accepter avant de payer
 * @param commandable    aucune ligne indisponible, en quantité excessive ou au prix non accepté
 * @param avertissements messages ponctuels de la dernière action (quantité plafonnée, lignes fusionnées…)
 */
public record PanierReponse(
        List<LignePanierReponse> lignes,
        int nombreArticles,
        long sousTotal,
        CodePromoReponse codePromo,
        long remise,
        Long fraisLivraison,
        boolean livraisonOfferte,
        long total,
        boolean prixModifies,
        boolean commandable,
        List<String> avertissements) {

    /**
     * @param valide  le code s'applique au panier actuel (sinon {@code message} explique pourquoi)
     * @param message raison du refus (« À partir de 50 000 FCFA d'achat »), ou description de l'avantage
     */
    public record CodePromoReponse(String code, boolean valide, String erreur, String message) {
    }

    public static PanierReponse vide() {
        return new PanierReponse(List.of(), 0, 0, null, 0, null, false, 0, false, false, List.of());
    }

    public PanierReponse avecAvertissements(List<String> messages) {
        return new PanierReponse(lignes, nombreArticles, sousTotal, codePromo, remise, fraisLivraison,
                livraisonOfferte, total, prixModifies, commandable, List.copyOf(messages));
    }
}
