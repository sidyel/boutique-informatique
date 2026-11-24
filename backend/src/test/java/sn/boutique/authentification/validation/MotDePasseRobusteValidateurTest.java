package sn.boutique.authentification.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MotDePasseRobusteValidateurTest {

    private final MotDePasseRobusteValidateur validateur = new MotDePasseRobusteValidateur();

    @Test
    void exigeDixCaracteresAuMoins() {
        assertThat(validateur.isValid("court-123", null)).isFalse();
        assertThat(validateur.isValid("dakar-2026", null)).isTrue();
    }

    @Test
    void limiteA72OctetsEtNonA72Caracteres() {
        assertThat(validateur.isValid("a".repeat(72), null)).isTrue();
        assertThat(validateur.isValid("a".repeat(73), null)).isFalse();
        // « é » occupe 2 octets en UTF-8 : 37 caractères = 74 octets
        assertThat(validateur.isValid("é".repeat(36), null)).isTrue();
        assertThat(validateur.isValid("é".repeat(37), null)).isFalse();
    }

    @Test
    void laisseLaValeurNulleANotBlank() {
        assertThat(validateur.isValid(null, null)).isTrue();
    }
}
