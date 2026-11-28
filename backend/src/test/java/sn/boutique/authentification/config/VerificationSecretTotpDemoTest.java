package sn.boutique.authentification.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import sn.boutique.noyau.config.ProprietesBoutique;

/** Le secret TOTP fixe de la démonstration est toléré en développement et en CI, jamais en production (ADR 0009 §10). */
class VerificationSecretTotpDemoTest {

    private static VerificationSecretTotpDemo verification(String secretFixe, boolean indexation, String... profils) {
        MockEnvironment environnement = new MockEnvironment();
        environnement.setActiveProfiles(profils);
        ProprietesSecondFacteur proprietes = new ProprietesSecondFacteur("x", null, "Boutique", secretFixe,
                Duration.ofMinutes(5), Duration.ofMinutes(30), 5, Duration.ofMinutes(10), Duration.ofHours(48),
                Duration.ofDays(1095), 10, Duration.ofMinutes(10), 30, Duration.ofMinutes(15));
        return new VerificationSecretTotpDemo(proprietes,
                new ProprietesBoutique("https://boutique.sn", List.of("https://boutique.sn"), indexation), environnement);
    }

    @Test
    void sansSecretFixeAucuneRestriction() {
        assertThatCode(() -> verification(null, true, "prod")).doesNotThrowAnyException();
        assertThatCode(() -> verification("  ", true, "prod")).doesNotThrowAnyException();
    }

    @Test
    void lesEnvironnementsDeProductionRefusentLeSecretFixe() {
        assertThatThrownBy(() -> verification("JBSWY3DPEHPK3PXP", false, "prod")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_ADMIN_TOTP_SECRET");
        assertThatThrownBy(() -> verification("JBSWY3DPEHPK3PXP", true, "dev")).isInstanceOf(IllegalStateException.class);
        // prod,demo avec indexation autorisée : une production déguisée en démonstration
        assertThatThrownBy(() -> verification("JBSWY3DPEHPK3PXP", true, "prod", "demo"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void laPileDeCiEtLeDeveloppementPeuventLUtiliser() {
        assertThatCode(() -> verification("JBSWY3DPEHPK3PXP", false, "prod", "demo")).doesNotThrowAnyException();
        assertThatCode(() -> verification("JBSWY3DPEHPK3PXP", false, "dev")).doesNotThrowAnyException();
    }
}
