package sn.boutique.noyau.telephone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TelephoneSenegalTest {

    @ParameterizedTest(name = "« {0} » → {1}")
    @CsvSource(delimiter = '|', value = {
            "77 123 45 67           | +221771234567",
            "771234567              | +221771234567",
            "77.123.45.67           | +221771234567",
            "77-123-45-67           | +221771234567",
            "+221 77 123 45 67      | +221771234567",
            "+221771234567          | +221771234567",
            "00221 77 123 45 67     | +221771234567",
            "221771234567           | +221771234567",
            "(+221) 78 000 11 22    | +221780001122",
            "70 555 66 77           | +221705556677",
            "75 100 20 30           | +221751002030",
            "76 999 88 77           | +221769998877",
            "33 821 00 00           | +221338210000",
            "'  77 123 45 67  '     | +221771234567",
    })
    void normaliseLesSaisiesCourantesEnE164(String saisie, String attendu) {
        assertThat(TelephoneSenegal.normaliser(saisie)).contains(attendu);
    }

    @ParameterizedTest(name = "« {0} » refusé")
    @ValueSource(strings = {
            "",
            "   ",
            "79 123 45 67",       // préfixe non attribué
            "74 123 45 67",
            "34 123 45 67",
            "77 123 45 6",        // 8 chiffres
            "77 123 45 678",      // 10 chiffres
            "+33 6 12 34 56 78",  // numéro français
            "+2217712345678",     // trop long après l'indicatif
            "77 12a 45 67",
            "221 77 123 45",      // 221 + 8 chiffres
    })
    void refuseLesNumerosNonSenegalais(String saisie) {
        assertThat(TelephoneSenegal.normaliser(saisie)).isEmpty();
        assertThat(TelephoneSenegal.estValide(saisie)).isFalse();
    }

    @Test
    void leFormatNormaliseRespecteLExpressionDeLaBase() {
        assertThat(TelephoneSenegal.FORMAT_E164.matcher("+221771234567").matches()).isTrue();
        assertThat(TelephoneSenegal.FORMAT_E164.matcher("771234567").matches()).isFalse();
    }

    @Test
    void normaliserOuEchouerLeveUneErreurMetier() {
        assertThatThrownBy(() -> TelephoneSenegal.normaliserOuEchouer("12345"))
                .isInstanceOf(TelephoneInvalideException.class)
                .hasMessage("Le numéro « 12345 » n'est pas un numéro sénégalais valide.");
    }

    @Test
    void unChampFacultatifVideDevientNull() {
        assertThat(TelephoneSenegal.normaliserFacultatif(null)).isNull();
        assertThat(TelephoneSenegal.normaliserFacultatif("  ")).isNull();
        assertThat(TelephoneSenegal.normaliserFacultatif("77 123 45 67")).isEqualTo("+221771234567");
    }

    @Test
    void fournitLeNumeroPourWhatsApp() {
        assertThat(TelephoneSenegal.pourWhatsApp("+221771234567")).isEqualTo("221771234567");
    }

    record RequeteEssai(@TelephoneSenegalais String telephone) {
    }

    @Test
    void lAnnotationDeValidationAccepteLesSaisiesValidesEtVides() {
        Validator validateur = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validateur.validate(new RequeteEssai("77 123 45 67"))).isEmpty();
        assertThat(validateur.validate(new RequeteEssai(null))).isEmpty();

        Set<ConstraintViolation<RequeteEssai>> erreurs = validateur.validate(new RequeteEssai("79 000 00 00"));
        assertThat(erreurs).singleElement()
                .extracting(ConstraintViolation::getMessage)
                .isEqualTo("Numéro de téléphone sénégalais invalide (ex. 77 123 45 67)");
    }
}
