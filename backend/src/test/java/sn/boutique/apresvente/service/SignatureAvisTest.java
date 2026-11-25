package sn.boutique.apresvente.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Signature publique d'un avis : prénom et initiale, jamais de nom de famille complet. */
class SignatureAvisTest {

    @Test
    void lePrenomEstSuiviDeLInitialeDuNom() {
        assertThat(AvisPublicService.prenomEtInitiale("Awa D.", true)).isEqualTo("Awa D.");
        assertThat(AvisPublicService.prenomEtInitiale("moussa   diop", false)).isEqualTo("Moussa D.");
        assertThat(AvisPublicService.prenomEtInitiale(" Éric Ndiaye Sarr ", true)).isEqualTo("Éric N.");
        assertThat(AvisPublicService.prenomEtInitiale("Aïssatou", true)).isEqualTo("Aïssatou");
    }

    @Test
    void sansNomLAvisEstSigneSelonLAchat() {
        assertThat(AvisPublicService.prenomEtInitiale("  ", true)).isEqualTo("Client vérifié");
        assertThat(AvisPublicService.prenomEtInitiale(null, false)).isEqualTo("Client");
    }
}
