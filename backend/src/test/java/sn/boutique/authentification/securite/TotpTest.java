package sn.boutique.authentification.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/** Vecteurs de test de la RFC 6238 (annexe B, HMAC-SHA1, secret ASCII « 12345678901234567890 », 8 chiffres). */
class TotpTest {

    private static final byte[] SECRET_RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void vecteursDeLaRfc6238() {
        long[][] temps = {{59L}, {1111111109L}, {1111111111L}, {1234567890L}, {2000000000L}, {20000000000L}};
        String[] attendus = {"94287082", "07081804", "14050471", "89005924", "69279037", "65353130"};
        for (int i = 0; i < temps.length; i++) {
            long pas = Totp.pas(Instant.ofEpochSecond(temps[i][0]));
            assertThat(Totp.code(SECRET_RFC, pas, 8)).as("instant %d", temps[i][0]).isEqualTo(attendus[i]);
        }
    }

    @Test
    void sixChiffresSontLesSixDerniersDuCodeAHuitChiffres() {
        long pas = Totp.pas(Instant.ofEpochSecond(59));
        assertThat(Totp.code(SECRET_RFC, pas, 6)).isEqualTo("287082");
    }

    @Test
    void accepteLePasCourantEtSesVoisinsMaisPasPlusLoin() {
        Instant t = Instant.ofEpochSecond(1_700_000_000L);
        long pas = Totp.pas(t);
        for (long decalage = -1; decalage <= 1; decalage++) {
            String code = Totp.code(SECRET_RFC, pas + decalage, 6);
            assertThat(Totp.verifier(SECRET_RFC, code, t)).as("décalage %d", decalage).hasValue(pas + decalage);
        }
        assertThat(Totp.verifier(SECRET_RFC, Totp.code(SECRET_RFC, pas + 2, 6), t)).isEqualTo(OptionalLong.empty());
        assertThat(Totp.verifier(SECRET_RFC, Totp.code(SECRET_RFC, pas - 2, 6), t)).isEqualTo(OptionalLong.empty());
    }

    @Test
    void refuseLesCodesMalFormes() {
        Instant t = Instant.ofEpochSecond(1_700_000_000L);
        for (String saisi : new String[] {null, "", "12345", "1234567", "abcdef", "12 34 5a"}) {
            assertThat(Totp.verifier(SECRET_RFC, saisi, t)).as("saisie %s", saisi).isEmpty();
        }
    }

    @Test
    void toleLesEspacesDansLaSaisie() {
        Instant t = Instant.ofEpochSecond(1_700_000_000L);
        String code = Totp.code(SECRET_RFC, Totp.pas(t), 6);
        assertThat(Totp.verifier(SECRET_RFC, code.substring(0, 3) + " " + code.substring(3), t)).isPresent();
    }

    @Test
    void secretGenereDe160BitsEnBase32() {
        String secret = Totp.genererSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Base32.decoder(secret)).hasSize(20);
        assertThat(Totp.genererSecret()).isNotEqualTo(secret);
    }

    @Test
    void base32SuitLesVecteursDeLaRfc4648() {
        String[][] vecteurs = {{"f", "MY"}, {"fo", "MZXQ"}, {"foo", "MZXW6"}, {"foob", "MZXW6YQ"}, {"fooba", "MZXW6YTB"},
                {"foobar", "MZXW6YTBOI"}};
        for (String[] v : vecteurs) {
            assertThat(Base32.encoder(v[0].getBytes(StandardCharsets.US_ASCII))).isEqualTo(v[1]);
            assertThat(new String(Base32.decoder(v[1]), StandardCharsets.US_ASCII)).isEqualTo(v[0]);
        }
        assertThat(new String(Base32.decoder("mzxw 6ytb-oi=="), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        assertThatThrownBy(() -> Base32.decoder("MZ1W")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uriOtpauthEstEchappee() {
        String uri = Totp.uri("Boutique & Co", "awa@exemple.sn", "ABC234");
        assertThat(uri).isEqualTo("otpauth://totp/Boutique%20%26%20Co:awa%40exemple.sn?secret=ABC234"
                + "&issuer=Boutique%20%26%20Co&algorithm=SHA1&digits=6&period=30");
    }
}
