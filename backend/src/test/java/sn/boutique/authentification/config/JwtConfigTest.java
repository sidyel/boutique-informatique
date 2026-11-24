package sn.boutique.authentification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class JwtConfigTest {

    private static final String CLE_VALIDE = Base64.getEncoder().encodeToString(new byte[48]);
    private final JwtConfig config = new JwtConfig();

    @Test
    void accepteUneCleBase64DeTrenteDeuxOctetsOuPlus() {
        assertThat(JwtConfig.lireCle(CLE_VALIDE).getEncoded()).hasSize(48);
        assertThat(JwtConfig.lireCle(Base64.getEncoder().encodeToString(new byte[32])).getEncoded()).hasSize(32);
    }

    @Test
    void refuseUneCleAbsenteInvalideOuTropCourte() {
        assertThatThrownBy(() -> JwtConfig.lireCle(null)).hasMessageContaining("absent");
        assertThatThrownBy(() -> JwtConfig.lireCle("  ")).hasMessageContaining("absent");
        assertThatThrownBy(() -> JwtConfig.lireCle("a-remplacer-par-une-cle")).hasMessageContaining("Base64");
        assertThatThrownBy(() -> JwtConfig.lireCle(Base64.getEncoder().encodeToString(new byte[31])))
                .hasMessageContaining("trop court");
    }

    @Test
    void enProductionUneCleInvalideEmpecheLeDemarrage() {
        assertThatThrownBy(() -> config.cleSignatureJwt(proprietes("", false)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.cleSignatureJwt(proprietes("trop-court", false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enDeveloppementUneCleJetableEstGeneree() {
        assertThat(config.cleSignatureJwt(proprietes("", true)).getEncoded()).hasSize(48);
        assertThat(config.cleSignatureJwt(proprietes("a-remplacer", true)).getEncoded()).hasSize(48);
        // Une clé valide est utilisée telle quelle, même en dev
        assertThat(config.cleSignatureJwt(proprietes(CLE_VALIDE, true)).getEncoded()).isEqualTo(new byte[48]);
    }

    private static ProprietesAuthentification proprietes(String secret, boolean ephemere) {
        return new ProprietesAuthentification(secret, ephemere, "essai", Duration.ofMinutes(15),
                Duration.ofDays(30), Duration.ofHours(12), 5, Duration.ofMinutes(15),
                new ProprietesAuthentification.Cookie("jeton_rafraichissement", "/api/auth", true));
    }
}
