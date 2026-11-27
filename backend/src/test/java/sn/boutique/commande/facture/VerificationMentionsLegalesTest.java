package sn.boutique.commande.facture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import sn.boutique.noyau.config.ProprietesBoutique;

/** Les mentions légales vides avertissent en développement et bloquent la production. */
@ExtendWith(OutputCaptureExtension.class)
class VerificationMentionsLegalesTest {

    private static VerificationMentionsLegales verification(ProprietesFacture facture, boolean indexation,
                                                            String... profils) {
        MockEnvironment environnement = new MockEnvironment();
        environnement.setActiveProfiles(profils);
        return new VerificationMentionsLegales(facture,
                new ProprietesBoutique("https://boutique.sn", List.of("https://boutique.sn"), indexation), environnement);
    }

    private static final ProprietesFacture VIDE = new ProprietesFacture("", null, " ", "", true);
    private static final ProprietesFacture COMPLETE = new ProprietesFacture("Boutique SARL", "0012345", "SN-DKR-2026-B-1",
            "18 %", true);

    @Test
    void enDeveloppementLesMentionsVidesDonnentUnAvertissementSansBloquer(CapturedOutput sortie) {
        assertThatCode(() -> verification(VIDE, false, "dev").verifier()).doesNotThrowAnyException();

        assertThat(sortie.getOut()).contains("AVERTISSEMENT", "FACTURE_RAISON_SOCIALE", "FACTURE_NINEA",
                "FACTURE_RCCM", "FACTURE_TVA");
    }

    @Test
    void lIndexationAutoriseeBloqueTantQueLesMentionsSontVides() {
        assertThatThrownBy(() -> verification(VIDE, true, "dev").verifier())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("FACTURE_NINEA");
    }

    @Test
    void leProfilProdBloqueMaisPasLaDemonstration() {
        assertThatThrownBy(() -> verification(VIDE, false, "prod").verifier())
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> verification(VIDE, false, "prod", "demo").verifier()).doesNotThrowAnyException();
        // Désactivation explicite (pile de développement locale en profil prod)
        ProprietesFacture facultatives = new ProprietesFacture(null, null, null, null, false);
        assertThatCode(() -> verification(facultatives, false, "prod").verifier()).doesNotThrowAnyException();
    }

    @Test
    void desMentionsCompletesNeBloquentJamaisEtNeFontAucuneAlerte(CapturedOutput sortie) {
        assertThatCode(() -> verification(COMPLETE, true, "prod").verifier()).doesNotThrowAnyException();
        assertThat(sortie.getOut()).doesNotContain("AVERTISSEMENT");
    }
}
