package sn.boutique.authentification;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.administration.journal.JournalAdministration;
import sn.boutique.administration.securite.ReauthentificationRequise;

/**
 * Routes d'administration factices, présentes seulement dans le contexte de test : elles servent à vérifier le journal
 * (ligne détaillée, ligne générique, masquage) et la réauthentification sans toucher aux données du site.
 */
@RestController
@RequestMapping("/api/admin/test-outils")
class ControleurJournalDeTest {

    static final String PREFIXE = "/api/admin/test-outils";

    private final JournalAdministration journal;

    ControleurJournalDeTest(JournalAdministration journal) {
        this.journal = journal;
    }

    /** N'écrit rien : l'intercepteur doit ajouter la ligne générique. */
    @PostMapping("/silencieuse")
    ResponseEntity<Void> silencieuse() {
        return ResponseEntity.noContent().build();
    }

    /** Écrit sa ligne détaillée, avec des données personnelles que le journal doit masquer. */
    @PostMapping("/detaillee")
    ResponseEntity<Void> detaillee() {
        journal.enregistrer("TEST_DETAILLE", "Test", 42, Map.of("statut", "A", "email", "client@exemple.sn"),
                Map.of("statut", "B", "telephone", "+221771234567", "nom", "Diop", "montant", 1500));
        return ResponseEntity.noContent().build();
    }

    @ReauthentificationRequise
    @PostMapping("/sensible")
    ResponseEntity<Void> sensible() {
        return ResponseEntity.noContent().build();
    }

    /** Échoue après avoir écrit : la ligne du journal doit disparaître avec le reste de la transaction. */
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/echoue")
    ResponseEntity<Void> echoue() {
        journal.enregistrer("TEST_ANNULE", "Test", 1, null, null);
        throw new IllegalStateException("échec voulu");
    }
}
