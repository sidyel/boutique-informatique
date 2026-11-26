package sn.boutique.marketing.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import sn.boutique.marketing.modele.CodePromo;
import sn.boutique.marketing.modele.TypeCodePromo;

/**
 * Calcul d'un code promo sur un panier : fonction pure (aucune base), utilisée par le panier et par la
 * commande (phase 4b) pour garantir le même résultat aux deux endroits.
 * <p>
 * Règles :
 * <ul>
 *   <li>période et activation ({@link CodePromo#estValideA}) ; plafond total ({@code nombreUtilisations}, qui
 *   compte aussi les utilisations réservées par des commandes en cours) ; plafond par client ;</li>
 *   <li>montant minimum comparé au sous-total du panier entier ;</li>
 *   <li>catégories ciblées : seules les lignes d'une catégorie ciblée (ou de l'une de ses sous-catégories)
 *   bénéficient de la remise ; aucune ligne éligible → le code ne s'applique pas ;</li>
 *   <li>pourcentage arrondi à l'unité inférieure ; montant fixe plafonné au sous-total éligible ; livraison
 *   offerte : remise nulle, frais de livraison à 0.</li>
 * </ul>
 */
public final class EvaluationCodePromo {

    private EvaluationCodePromo() {
    }

    /**
     * @param montant      total de la ligne (prix actuel × quantité)
     * @param categorieIds catégorie du produit et toutes ses catégories parentes
     */
    public record Ligne(long montant, Set<Long> categorieIds) {
    }

    /**
     * @param erreur code stable du refus (null si le code s'applique)
     */
    public record Resultat(boolean valide, String erreur, String message, long remise, boolean livraisonOfferte) {

        static Resultat refus(String erreur, String message) {
            return new Resultat(false, erreur, message, 0, false);
        }
    }

    /** Refus qui empêchent d'enregistrer le code sur le panier (le client ne peut rien y faire). */
    public static final Set<String> REFUS_DEFINITIFS = Set.of(
            "CODE_PROMO_INCONNU", "CODE_PROMO_EXPIRE", "CODE_PROMO_EPUISE", "CODE_PROMO_DEJA_UTILISE");

    /**
     * @param utilisationsClient commandes non annulées du client avec ce code (null si le client est inconnu :
     *                           invité au stade du panier, le contrôle est refait à la commande par téléphone)
     */
    public static Resultat evaluer(CodePromo code, List<Ligne> lignes, Integer utilisationsClient, Instant instant) {
        if (code == null || !code.isActif()) {
            return Resultat.refus("CODE_PROMO_INCONNU", "Ce code promo n'existe pas.");
        }
        if (!code.estValideA(instant)) {
            boolean pasEncore = code.getDateDebut() != null && instant.isBefore(code.getDateDebut());
            return Resultat.refus("CODE_PROMO_EXPIRE", pasEncore
                    ? "Ce code promo n'est pas encore valable."
                    : "Ce code promo a expiré.");
        }
        if (code.getUtilisationsMax() != null && code.getNombreUtilisations() >= code.getUtilisationsMax()) {
            return Resultat.refus("CODE_PROMO_EPUISE", "Ce code promo a atteint son nombre maximal d'utilisations.");
        }
        if (utilisationsClient != null && code.getUtilisationsParClient() != null
                && utilisationsClient >= code.getUtilisationsParClient()) {
            return Resultat.refus("CODE_PROMO_DEJA_UTILISE", "Vous avez déjà utilisé ce code promo.");
        }
        long sousTotal = lignes.stream().mapToLong(Ligne::montant).sum();
        if (sousTotal < code.getMontantMinimum()) {
            return Resultat.refus("CODE_PROMO_MINIMUM",
                    "Ce code s'applique à partir de %s d'achat.".formatted(fcfa(code.getMontantMinimum())));
        }
        Set<Long> ciblees = code.getCategoriesCiblees().stream().map(categorie -> categorie.getId())
                .collect(Collectors.toSet());
        long eligible = lignes.stream()
                .filter(ligne -> ciblees.isEmpty() || ligne.categorieIds().stream().anyMatch(ciblees::contains))
                .mapToLong(Ligne::montant)
                .sum();
        if (eligible == 0) {
            return Resultat.refus("CODE_PROMO_NON_APPLICABLE",
                    "Ce code ne s'applique à aucun article de votre panier.");
        }
        TypeCodePromo type = code.getType();
        return switch (type) {
            case POURCENTAGE -> new Resultat(true, null, "-%d %%".formatted(code.getValeur()),
                    Math.floorDiv(eligible * code.getValeur(), 100), false);
            case MONTANT_FIXE -> new Resultat(true, null, "-" + fcfa(code.getValeur()),
                    Math.min(code.getValeur(), eligible), false);
            case LIVRAISON_OFFERTE -> new Resultat(true, null, "Livraison offerte", 0, true);
        };
    }

    /** 50000 → « 50 000 FCFA » (espace fine insécable, comme sur le site). */
    static String fcfa(long montant) {
        String chiffres = Long.toString(montant);
        StringBuilder resultat = new StringBuilder();
        for (int i = 0; i < chiffres.length(); i++) {
            if (i > 0 && (chiffres.length() - i) % 3 == 0) {
                resultat.append(' ');
            }
            resultat.append(chiffres.charAt(i));
        }
        return resultat.append(" FCFA").toString();
    }
}
