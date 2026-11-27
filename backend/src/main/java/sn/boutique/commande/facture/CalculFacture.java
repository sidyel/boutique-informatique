package sn.boutique.commande.facture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Calculs des pièces comptables, entiers uniquement (FCFA sans décimales). Deux méthodes, documentées dans l'ADR 0008
 * et à faire valider par le comptable :
 * <ul>
 *   <li><strong>Répartition au plus fort reste</strong> d'un montant sur des lignes au prorata de leurs poids : la somme
 *   des parts est exactement le montant (la remise d'un code promo se répartit ainsi sur les articles) ;</li>
 *   <li><strong>TVA extraite du TTC</strong> : la TVA du document est calculée une fois, sur le total TTC
 *   ({@code TTC × taux / (1 + taux)}, arrondie au franc le plus proche, demi vers le haut), puis répartie sur les
 *   lignes au plus fort reste. La somme des TVA des lignes égale donc toujours la TVA du total, et HT + TVA = TTC
 *   sur chaque ligne comme sur le total.</li>
 * </ul>
 */
public final class CalculFacture {

    private CalculFacture() {
    }

    /** Part de TVA et de HT d'une ligne. */
    public record Part(long ht, long tva) {
    }

    /**
     * Répartit {@code montant} (≥ 0) sur des lignes de poids ≥ 0 : parts entières dont la somme est {@code montant}.
     * Poids tous nuls : tout va à la première ligne.
     */
    public static long[] repartir(long montant, List<Long> poids) {
        int n = poids.size();
        long[] parts = new long[n];
        if (n == 0 || montant == 0) {
            return parts;
        }
        long somme = poids.stream().mapToLong(Long::longValue).sum();
        if (somme == 0) {
            parts[0] = montant;
            return parts;
        }
        long[] restes = new long[n];
        long distribue = 0;
        for (int i = 0; i < n; i++) {
            // Pas de dépassement : montant et poids restent sous 10^12, le produit sous 10^24 → Math.multiplyHigh serait
            // nécessaire au-delà ; à l'échelle d'une commande (< 10^9 FCFA) le produit tient dans un long.
            long produit = Math.multiplyExact(montant, poids.get(i));
            parts[i] = produit / somme;
            restes[i] = produit % somme;
            distribue += parts[i];
        }
        completer(parts, restes, montant - distribue);
        return parts;
    }

    /**
     * TVA et HT de chaque ligne à partir de leur TTC. Régime non assujetti ({@code tauxBp} ignoré par l'appelant :
     * passer 0) : TVA nulle, HT = TTC.
     *
     * @param tauxBp points de base : 1800 = 18 %
     */
    public static List<Part> extraireTva(List<Long> ttc, int tauxBp) {
        List<Part> parts = new ArrayList<>(ttc.size());
        if (tauxBp == 0) {
            ttc.forEach(t -> parts.add(new Part(t, 0)));
            return parts;
        }
        long diviseur = 10_000L + tauxBp;
        long totalTtc = ttc.stream().mapToLong(Long::longValue).sum();
        // Arrondi au franc le plus proche, demi vers le haut
        long tvaTotale = (Math.multiplyExact(totalTtc, (long) tauxBp) * 2 + diviseur) / (2 * diviseur);
        int n = ttc.size();
        long[] tva = new long[n];
        long[] restes = new long[n];
        long distribue = 0;
        for (int i = 0; i < n; i++) {
            long produit = Math.multiplyExact(ttc.get(i), (long) tauxBp);
            tva[i] = produit / diviseur;
            restes[i] = produit % diviseur;
            distribue += tva[i];
        }
        completer(tva, restes, tvaTotale - distribue);
        for (int i = 0; i < n; i++) {
            parts.add(new Part(ttc.get(i) - tva[i], tva[i]));
        }
        return parts;
    }

    /** Ajoute 1 aux {@code reste} lignes ayant le plus fort reste (égalité : la première d'abord). */
    private static void completer(long[] parts, long[] restes, long reste) {
        Integer[] ordre = IntStream.range(0, parts.length).boxed().toArray(Integer[]::new);
        Arrays.sort(ordre, Comparator.<Integer>comparingLong(i -> -restes[i]).thenComparingInt(i -> i));
        for (int k = 0; k < reste; k++) {
            parts[ordre[k]]++;
        }
    }
}
