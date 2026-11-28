package sn.boutique.authentification.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import sn.boutique.authentification.config.ProprietesSecondFacteur;

class ChiffrementSecretsTest {

    private static final String CLE_A = Base64.getEncoder().encodeToString("a".repeat(32).getBytes());
    private static final String CLE_B = Base64.getEncoder().encodeToString("b".repeat(40).getBytes());

    private static ChiffrementSecrets avec(String courante, String precedente) {
        return new ChiffrementSecrets(new ProprietesSecondFacteur(courante, precedente, "Boutique", null,
                Duration.ofMinutes(5), Duration.ofMinutes(30), 5, Duration.ofMinutes(10), Duration.ofHours(48),
                Duration.ofDays(1095), 10, Duration.ofMinutes(10), 30, Duration.ofMinutes(15)));
    }

    @Test
    void chiffrePuisDechiffre() {
        ChiffrementSecrets c = avec(CLE_A, null);
        String chiffre = c.chiffrer("JBSWY3DPEHPK3PXP", 7L);
        assertThat(chiffre).startsWith("v1.").doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(c.dechiffrer(chiffre, 7L)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void deuxChiffrementsDuMemeSecretDifferent() {
        ChiffrementSecrets c = avec(CLE_A, null);
        assertThat(c.chiffrer("X", 1L)).isNotEqualTo(c.chiffrer("X", 1L));
    }

    @Test
    void unSecretCopieVersUnAutreCompteNeSeDechiffrePas() {
        ChiffrementSecrets c = avec(CLE_A, null);
        String chiffre = c.chiffrer("SECRET", 1L);
        assertThatThrownBy(() -> c.dechiffrer(chiffre, 2L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void uneValeurAlbereeEstRefusee() {
        ChiffrementSecrets c = avec(CLE_A, null);
        String chiffre = c.chiffrer("SECRET", 1L);
        String altere = chiffre.substring(0, chiffre.length() - 2) + (chiffre.endsWith("AA") ? "BB" : "AA");
        assertThatThrownBy(() -> c.dechiffrer(altere, 1L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.dechiffrer("n'importe quoi", 1L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.dechiffrer(null, 1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rotationDeCle() {
        String ancien = avec(CLE_A, null).chiffrer("SECRET", 3L);

        // Sans l'ancienne clé : illisible, et on le sait
        ChiffrementSecrets sansPrecedente = avec(CLE_B, null);
        assertThatThrownBy(() -> sansPrecedente.dechiffrer(ancien, 3L)).hasMessageContaining("Clé de chiffrement inconnue");

        // Avec l'ancienne clé en « précédente » : lisible, à réécrire, et la réécriture est sous la clé courante
        ChiffrementSecrets rotation = avec(CLE_B, CLE_A);
        assertThat(rotation.estSousCleCourante(ancien)).isFalse();
        String reecrit = rotation.chiffrer(rotation.dechiffrer(ancien, 3L), 3L);
        assertThat(rotation.estSousCleCourante(reecrit)).isTrue();
        assertThat(sansPrecedente.dechiffrer(reecrit, 3L)).isEqualTo("SECRET");
    }

    @Test
    void refuseDeDemarrerSansCleOuAvecUneCleTropCourte() {
        assertThatThrownBy(() -> avec(null, null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_CLE_CHIFFREMENT est absente");
        assertThatThrownBy(() -> avec("  ", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> avec(Base64.getEncoder().encodeToString(new byte[31]), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("trop courte");
        assertThatThrownBy(() -> avec("pas du base64 !!", null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
        assertThatThrownBy(() -> avec(CLE_A, Base64.getEncoder().encodeToString(new byte[5])))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PRECEDENTE");
    }

    @Test
    void trenteDeuxOctetsSuffisent() {
        assertThat(avec(Base64.getEncoder().encodeToString(new byte[32]), null).identifiantCleCourante()).hasSize(8);
    }
}
