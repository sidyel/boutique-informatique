package sn.boutique.authentification.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import sn.boutique.noyau.limitation.Comptage;
import sn.boutique.noyau.limitation.LimiteurDebit;
import sn.boutique.noyau.limitation.ProprietesLimitation;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;

/** Quota par e-mail seul : alerte sans blocage pour la connexion, envoi omis pour les e-mails (I1, M2). */
@ExtendWith(OutputCaptureExtension.class)
class LimitesAuthentificationTest {

    private static final Regle REGLE = new Regle(10, Duration.ofHours(1));
    private static final Regle ALARME = new Regle(100, Duration.ofHours(1));

    private final LimiteurDebit limiteur = mock(LimiteurDebit.class);
    private final LimitesAuthentification limites = new LimitesAuthentification(limiteur, new ProprietesLimitation(
            true, REGLE, REGLE, ALARME, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE, REGLE));

    @Test
    void auDelaDeCentTentativesSurUnEmailUneAlerteEstJournaliseeUneFoisSansBloquer(CapturedOutput sortie) {
        when(limiteur.compter(eq("connexion-email-alarme"), any(), anyString()))
                .thenReturn(new Comptage(100, 1_000), new Comptage(101, 1_000), new Comptage(102, 1_000));

        for (int i = 0; i < 3; i++) {
            assertThatNoException().as("la connexion n'est jamais bloquée par le quota de l'e-mail seul")
                    .isThrownBy(() -> limites.connexion("41.82.0.1", "Awa.Diop@exemple.sn"));
        }
        String journal = sortie.getOut();
        assertThat(journal.split(LimitesAuthentification.ALERTE_CONNEXION_CIBLEE, -1)).as("une seule alerte").hasSize(2);
        assertThat(journal).contains("ERROR").contains("plus de 100 tentatives").doesNotContain("Awa.Diop", "awa.diop");
    }

    @Test
    void auDelaDuQuotaDeLEmailLEnvoiEstOmisSansErreur() {
        when(limiteur.compter(eq("mot-de-passe-oublie-email"), any(), anyString()))
                .thenReturn(new Comptage(10, 1_000), new Comptage(11, 1_000));
        when(limiteur.compter(eq("renvoi-verification-email"), any(), anyString()))
                .thenReturn(new Comptage(11, 1_000));

        assertThat(limites.motDePasseOublie("41.82.0.1", "awa@exemple.sn")).isTrue();
        assertThat(limites.motDePasseOublie("41.82.0.2", "awa@exemple.sn")).isFalse();
        assertThat(limites.renvoiVerification("41.82.0.3", "awa@exemple.sn")).isFalse();
    }
}
