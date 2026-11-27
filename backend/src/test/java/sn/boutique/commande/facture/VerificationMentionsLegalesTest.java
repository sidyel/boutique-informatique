package sn.boutique.commande.facture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.modele.ParametresSite;
import sn.boutique.noyau.config.ProprietesBoutique;

/** Les mentions légales vides (lues en base) avertissent en développement et bloquent la production. */
@ExtendWith(OutputCaptureExtension.class)
class VerificationMentionsLegalesTest {

    private static VerificationMentionsLegales verification(ParametresSite parametres, boolean obligatoires,
                                                            boolean indexation, String... profils) {
        MockEnvironment environnement = new MockEnvironment();
        environnement.setActiveProfiles(profils);
        ParametresSiteDepot depot = Mockito.mock(ParametresSiteDepot.class);
        Mockito.when(depot.lire()).thenCallRealMethod();
        Mockito.when(depot.findById(ParametresSite.ID_UNIQUE)).thenReturn(Optional.of(parametres));
        return new VerificationMentionsLegales(new ProprietesFacture(obligatoires),
                new ProprietesBoutique("https://boutique.sn", List.of("https://boutique.sn"), indexation),
                environnement, depot);
    }

    private static ParametresSite vide() {
        return parametres(null, " ", "", null);
    }

    private static ParametresSite complete() {
        return parametres("Boutique SARL", "0012345", "SN-DKR-2026-B-1", "Avenue Cheikh Anta Diop, Dakar");
    }

    private static ParametresSite parametres(String raison, String ninea, String rccm, String siege) {
        ParametresSite p = Mockito.mock(ParametresSite.class);
        Mockito.when(p.getFactureRaisonSociale()).thenReturn(raison);
        Mockito.when(p.getFactureNinea()).thenReturn(ninea);
        Mockito.when(p.getFactureRccm()).thenReturn(rccm);
        Mockito.when(p.getFactureAdresseSiege()).thenReturn(siege);
        return p;
    }

    @Test
    void enDeveloppementLesMentionsVidesDonnentUnAvertissementSansBloquer(CapturedOutput sortie) {
        assertThatCode(() -> verification(vide(), true, false, "dev").verifier()).doesNotThrowAnyException();

        assertThat(sortie.getOut()).contains("AVERTISSEMENT", "raison sociale", "NINEA", "RCCM", "adresse du siège",
                "/api/admin/parametres/facturation");
    }

    @Test
    void lIndexationAutoriseeBloqueTantQueLesMentionsSontVides() {
        assertThatThrownBy(() -> verification(vide(), true, true, "dev").verifier())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("NINEA");
    }

    @Test
    void leProfilProdBloqueMaisPasLaDemonstration() {
        assertThatThrownBy(() -> verification(vide(), true, false, "prod").verifier())
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> verification(vide(), true, false, "prod", "demo").verifier()).doesNotThrowAnyException();
        // Désactivation explicite (pile de développement locale en profil prod)
        assertThatCode(() -> verification(vide(), false, false, "prod").verifier()).doesNotThrowAnyException();
    }

    @Test
    void desMentionsCompletesNeBloquentJamaisEtNeFontAucuneAlerte(CapturedOutput sortie) {
        assertThatCode(() -> verification(complete(), true, true, "prod").verifier()).doesNotThrowAnyException();
        assertThat(sortie.getOut()).doesNotContain("AVERTISSEMENT");
    }
}
