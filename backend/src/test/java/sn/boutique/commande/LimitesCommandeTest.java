package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.commande.service.LimitesCommande;
import sn.boutique.commande.service.ProprietesCommande;
import sn.boutique.noyau.limitation.LimiteurDebit;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;
import sn.boutique.noyau.limitation.TropDeRequetesException;

/**
 * Limites de débit de la commande : par IP (large, CGNAT) puis par numéro de téléphone haché. Réglables.
 */
class LimitesCommandeTest {

    private static final Regle PAR_IP = new Regle(20, Duration.ofHours(1));
    private static final Regle PAR_TELEPHONE = new Regle(8, Duration.ofHours(1));
    private static final Regle SUIVI = new Regle(120, Duration.ofMinutes(10));

    private final LimiteurDebit limiteur = mock(LimiteurDebit.class);
    private final LimitesCommande limites = new LimitesCommande(limiteur,
            new ProprietesCommande(PAR_IP, PAR_TELEPHONE, SUIVI, Duration.ofHours(24), Duration.ofMinutes(30),
                    Duration.ofSeconds(30), Duration.ofMillis(200), Duration.ofHours(2)));

    @Test
    void lIpPuisLeTelephoneNormaliseEtHacheSontLimites() {
        limites.creation("41.82.0.1", "77 123 45 67");

        InOrder ordre = inOrder(limiteur);
        ordre.verify(limiteur).verifier("commande-ip", PAR_IP, "41.82.0.1");
        ArgumentCaptor<String> cle = ArgumentCaptor.forClass(String.class);
        ordre.verify(limiteur).verifier(eq("commande-telephone"), eq(PAR_TELEPHONE), cle.capture());
        // Le même numéro écrit autrement compte pour le même client ; aucun numéro en clair dans Redis
        assertThat(cle.getValue()).isEqualTo(GenerateurJetons.empreinte("+221771234567"))
                .doesNotContain("771234567");
    }

    @Test
    void uneIpAuDelaDeSaLimiteNeConsommePasLeQuotaDuTelephone() {
        doThrow(new TropDeRequetesException("commande-ip", 600)).when(limiteur).verifier(eq("commande-ip"), any(), anyString());

        assertThatThrownBy(() -> limites.creation("41.82.0.1", "771234567")).isInstanceOf(TropDeRequetesException.class);
        verify(limiteur, never()).verifier(eq("commande-telephone"), any(), anyString());
    }

    @Test
    void leSuiviEstLimiteParIp() {
        limites.suivi("41.82.0.1");

        verify(limiteur).verifier("suivi-ip", SUIVI, "41.82.0.1");
    }
}
