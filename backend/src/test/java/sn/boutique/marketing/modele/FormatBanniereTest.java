package sn.boutique.marketing.modele;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FormatBanniereTest {

    @Test
    void lesFormatsConseillesNeDonnentAucunAvertissement() {
        assertThat(FormatBanniere.ORDINATEUR.avertissements(1920, 600)).isEmpty();
        assertThat(FormatBanniere.ORDINATEUR.avertissements(2400, 800)).as("3:1, dans la tolérance").isEmpty();
        assertThat(FormatBanniere.MOBILE.avertissements(800, 1000)).isEmpty();
        assertThat(FormatBanniere.MOBILE.avertissements(1080, 1350)).isEmpty();
    }

    @Test
    void desProportionsEloigneesDonnentUnAvertissement() {
        assertThat(FormatBanniere.ORDINATEUR.avertissements(1920, 1920)).singleElement().asString()
                .contains("Proportions 1,00:1").contains("1920 × 600");
        assertThat(FormatBanniere.MOBILE.avertissements(1920, 600)).singleElement().asString()
                .contains("800 × 1000").contains("recadrée");
    }

    @Test
    void uneImageTropEtroiteDonneUnAvertissement() {
        assertThat(FormatBanniere.ORDINATEUR.avertissements(1000, 312)).singleElement().asString()
                .contains("1000 px de large").contains("1440 px");
        assertThat(FormatBanniere.MOBILE.avertissements(600, 450)).hasSize(2);
    }
}
