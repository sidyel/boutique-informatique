package sn.boutique.marketing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import sn.boutique.marketing.modele.CodePromo;
import sn.boutique.marketing.modele.TypeCodePromo;
import sn.boutique.marketing.service.EvaluationCodePromo.Ligne;
import sn.boutique.marketing.service.EvaluationCodePromo.Resultat;

class EvaluationCodePromoTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-26T10:00:00Z");
    private static final List<Ligne> PANIER = List.of(new Ligne(33_333, Set.of(1L)), new Ligne(66_667, Set.of(2L)));

    @Test
    void pourcentageArrondiALUniteInferieure() {
        Resultat resultat = EvaluationCodePromo.evaluer(new CodePromo("DIX", TypeCodePromo.POURCENTAGE, 7),
                List.of(new Ligne(33_333, Set.of())), null, MAINTENANT);

        assertThat(resultat.valide()).isTrue();
        assertThat(resultat.remise()).isEqualTo(2_333); // 2 333,31
        assertThat(resultat.message()).isEqualTo("-7 %");
    }

    @Test
    void plafondParClientSeulementQuandLeClientEstConnu() {
        CodePromo code = new CodePromo("UNEFOIS", TypeCodePromo.MONTANT_FIXE, 5_000);
        code.setUtilisationsParClient(1);

        assertThat(EvaluationCodePromo.evaluer(code, PANIER, 1, MAINTENANT).erreur())
                .isEqualTo("CODE_PROMO_DEJA_UTILISE");
        assertThat(EvaluationCodePromo.evaluer(code, PANIER, 0, MAINTENANT).valide()).isTrue();
        // Invité : contrôlé à la commande, avec le téléphone
        assertThat(EvaluationCodePromo.evaluer(code, PANIER, null, MAINTENANT).valide()).isTrue();
    }

    @Test
    void finDePeriodeExclueEtDebutInclus() {
        CodePromo code = new CodePromo("PERIODE", TypeCodePromo.POURCENTAGE, 10);
        code.setDateDebut(MAINTENANT);
        code.setDateFin(MAINTENANT.plusSeconds(60));

        assertThat(EvaluationCodePromo.evaluer(code, PANIER, null, MAINTENANT).valide()).isTrue();
        assertThat(EvaluationCodePromo.evaluer(code, PANIER, null, MAINTENANT.plusSeconds(60)).erreur())
                .isEqualTo("CODE_PROMO_EXPIRE");
    }

    @Test
    void codeAbsentOuInactifEstInconnuEtLeMontantEstFormateCommeSurLeSite() {
        CodePromo inactif = new CodePromo("ARRET", TypeCodePromo.POURCENTAGE, 10);
        inactif.setActif(false);

        assertThat(EvaluationCodePromo.evaluer(null, PANIER, null, MAINTENANT).erreur()).isEqualTo("CODE_PROMO_INCONNU");
        assertThat(EvaluationCodePromo.evaluer(inactif, PANIER, null, MAINTENANT).erreur())
                .isEqualTo("CODE_PROMO_INCONNU");
        assertThat(EvaluationCodePromo.fcfa(1_250_000)).isEqualTo("1 250 000 FCFA");
        assertThat(EvaluationCodePromo.fcfa(500)).isEqualTo("500 FCFA");
    }
}
