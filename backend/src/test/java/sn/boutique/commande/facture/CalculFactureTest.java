package sn.boutique.commande.facture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import sn.boutique.commande.facture.CalculFacture.Part;

/** Répartition au plus fort reste et extraction de la TVA : cohérence des sommes, sur des cas fixes et aléatoires. */
class CalculFactureTest {

    @Test
    void laRepartitionAuPlusFortResteSommeExactementAuMontant() {
        assertThat(CalculFacture.repartir(5_000, List.of(33_333L, 30_003L)))
                .containsExactly(2_631, 2_369);
        assertThat(CalculFacture.repartir(100, List.of(1L, 1L, 1L))).containsExactly(34, 33, 33);
        assertThat(CalculFacture.repartir(0, List.of(10L, 20L))).containsExactly(0, 0);
        assertThat(CalculFacture.repartir(10, List.of(0L, 0L))).containsExactly(10, 0);
        assertThat(CalculFacture.repartir(10, List.of())).isEmpty();
    }

    @Test
    void laTvaEstExtraiteDuTtcEtArrondieAuFrancLePlusProche() {
        // 118 000 TTC à 18 % : 18 000 de TVA, 100 000 HT
        assertThat(CalculFacture.extraireTva(List.of(118_000L), 1800)).containsExactly(new Part(100_000, 18_000));
        // 12 000 × 18 / 118 = 1 830,508… → 1 831
        assertThat(CalculFacture.extraireTva(List.of(12_000L), 1800)).containsExactly(new Part(10_169, 1_831));
        // Régime non assujetti : pas de TVA
        assertThat(CalculFacture.extraireTva(List.of(12_000L, 5L), 0))
                .containsExactly(new Part(12_000, 0), new Part(5, 0));
        // Ligne gratuite
        assertThat(CalculFacture.extraireTva(List.of(0L, 118L), 1800)).containsExactly(new Part(0, 0), new Part(100, 18));
    }

    @Test
    void laSommeDesTvaDesLignesEgaleToujoursLaTvaDuTotal() {
        Random hasard = new Random(20_261_127L);
        for (int essai = 0; essai < 2_000; essai++) {
            int n = 1 + hasard.nextInt(8);
            int taux = new int[]{1800, 1000, 550, 2000}[hasard.nextInt(4)];
            List<Long> ttc = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ttc.add((long) hasard.nextInt(2_000_000));
            }
            long total = ttc.stream().mapToLong(Long::longValue).sum();

            List<Part> parts = CalculFacture.extraireTva(ttc, taux);

            long tvaTotale = Math.round((double) total * taux / (10_000 + taux));
            assertThat(parts.stream().mapToLong(Part::tva).sum()).as("TVA du total (essai %d)", essai)
                    .isBetween(tvaTotale - 1, tvaTotale + 1);
            assertThat(parts.stream().mapToLong(Part::tva).sum()).isEqualTo(
                    (total * taux * 2 + (10_000 + taux)) / (2L * (10_000 + taux)));
            for (int i = 0; i < n; i++) {
                assertThat(parts.get(i).ht() + parts.get(i).tva()).isEqualTo(ttc.get(i));
                assertThat(parts.get(i).tva()).isGreaterThanOrEqualTo(0);
                // Une ligne ne s'écarte jamais de plus d'un franc de sa TVA exacte
                assertThat((double) parts.get(i).tva()).isBetween(
                        ttc.get(i) * (double) taux / (10_000 + taux) - 1, ttc.get(i) * (double) taux / (10_000 + taux) + 1);
            }
        }
    }

    @Test
    void laRepartitionAleatoireSommeToujoursAuMontant() {
        Random hasard = new Random(7_919L);
        for (int essai = 0; essai < 2_000; essai++) {
            int n = 1 + hasard.nextInt(8);
            List<Long> poids = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                poids.add((long) hasard.nextInt(1_000_000));
            }
            long montant = hasard.nextInt(500_000);

            long[] parts = CalculFacture.repartir(montant, poids);

            long somme = 0;
            for (long part : parts) {
                assertThat(part).isGreaterThanOrEqualTo(0);
                somme += part;
            }
            assertThat(somme).as("essai %d", essai).isEqualTo(montant);
        }
    }
}
