package sn.boutique.noyau.limitation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;
import sn.boutique.noyau.config.ProprietesBoutique;

/** Limitation du débit coupée : tolérée en développement, démonstration et CI ; démarrage refusé en production. */
class VerificationLimitationDebitTest {

    private static VerificationLimitationDebit verification(boolean actif, boolean indexation, String... profils) {
        MockEnvironment environnement = new MockEnvironment();
        environnement.setActiveProfiles(profils);
        ProprietesLimitation limitation = Mockito.mock(ProprietesLimitation.class);
        Mockito.when(limitation.actif()).thenReturn(actif);
        return new VerificationLimitationDebit(limitation,
                new ProprietesBoutique("https://boutique.sn", List.of("https://boutique.sn"), indexation), environnement);
    }

    @Test
    void laLimitationActiveNeBloqueJamais() {
        assertThatCode(() -> verification(true, true, "prod")).doesNotThrowAnyException();
    }

    @Test
    void lesProfilsProdEtIndexationAutoriseeRefusentLeDemarrageSansLimitation() {
        assertThatThrownBy(() -> verification(false, false, "prod")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("limitation du débit");
        assertThatThrownBy(() -> verification(false, true, "dev")).isInstanceOf(IllegalStateException.class);
        // prod,demo avec indexation autorisée : c'est une production déguisée en démonstration
        assertThatThrownBy(() -> verification(false, true, "prod", "demo")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void laPileDeCiEtLeDeveloppementPeuventCouperLaLimitation() {
        // La CI Lighthouse tourne en « prod,demo » avec INDEXATION_AUTORISEE=false
        assertThatCode(() -> verification(false, false, "prod", "demo")).doesNotThrowAnyException();
        assertThatCode(() -> verification(false, false, "dev")).doesNotThrowAnyException();
    }
}
