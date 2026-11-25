package sn.boutique.noyau.limitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;
import sn.boutique.support.HorlogeReglable;

/** Limiteur de secours en mémoire locale (Redis en erreur) : fenêtre fixe, taille bornée. */
class LimiteurLocalTest {

    private static final Regle DEUX_PAR_MINUTE = new Regle(2, Duration.ofMinutes(1));

    private final HorlogeReglable horloge = new HorlogeReglable();

    @Test
    void appliqueLaRegleSurUneFenetreFixe() {
        LimiteurLocal limiteur = new LimiteurLocal(horloge);
        limiteur.verifier("connexion-ip", DEUX_PAR_MINUTE, "41.82.0.1");
        limiteur.verifier("connexion-ip", DEUX_PAR_MINUTE, "41.82.0.1");

        assertThatThrownBy(() -> limiteur.verifier("connexion-ip", DEUX_PAR_MINUTE, "41.82.0.1"))
                .isInstanceOfSatisfying(TropDeRequetesException.class,
                        e -> assertThat(e.getReessayerDans()).isEqualTo(60));
        // Autre IP, autre règle : compteurs distincts
        assertThatNoException().isThrownBy(() -> limiteur.verifier("connexion-ip", DEUX_PAR_MINUTE, "41.82.0.2"));
        assertThatNoException().isThrownBy(() -> limiteur.verifier("inscription-ip", DEUX_PAR_MINUTE, "41.82.0.1"));

        horloge.avancer(Duration.ofSeconds(45));
        assertThatThrownBy(() -> limiteur.verifier("connexion-ip", DEUX_PAR_MINUTE, "41.82.0.1"))
                .isInstanceOfSatisfying(TropDeRequetesException.class,
                        e -> assertThat(e.getReessayerDans()).isEqualTo(15));

        horloge.avancer(Duration.ofSeconds(15));
        assertThatNoException().as("nouvelle fenêtre")
                .isThrownBy(() -> limiteur.verifier("connexion-ip", DEUX_PAR_MINUTE, "41.82.0.1"));
    }

    @Test
    void resteBorneMemeSousUneAvalancheDeClesInventees() {
        LimiteurLocal limiteur = new LimiteurLocal(horloge, 100);
        limiteur.verifier("connexion-ip", DEUX_PAR_MINUTE, "attaquant");
        for (int i = 0; i < 10_000; i++) {
            limiteur.verifier("connexion-email", DEUX_PAR_MINUTE, "email-invente-" + i);
            // L'attaquant touche son propre compteur à chaque requête : il reste parmi les plus récents
            if (i == 5_000) {
                limiteur.verifier("connexion-ip", DEUX_PAR_MINUTE, "attaquant");
            }
        }
        assertThat(limiteur.taille()).isEqualTo(100);
        assertThat(LimiteurLocal.ENTREES_MAX).isEqualTo(10_000);
    }
}
