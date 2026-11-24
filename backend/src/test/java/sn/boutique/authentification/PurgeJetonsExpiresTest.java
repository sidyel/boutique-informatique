package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import sn.boutique.authentification.config.ProprietesAuthentification;
import sn.boutique.authentification.depot.JetonRafraichissementDepot;
import sn.boutique.authentification.depot.JetonReinitialisationMotDePasseDepot;
import sn.boutique.authentification.depot.JetonVerificationEmailDepot;
import sn.boutique.authentification.modele.JetonRafraichissement;
import sn.boutique.authentification.modele.JetonReinitialisationMotDePasse;
import sn.boutique.authentification.modele.JetonVerificationEmail;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.authentification.service.PurgeJetonsExpires;
import sn.boutique.authentification.service.PurgeJetonsExpires.Bilan;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.support.FabriqueComptes;
import sn.boutique.support.HorlogeReglable;
import sn.boutique.support.TestDepot;

@TestDepot
@Import(HorlogeReglable.Configuration.class)
class PurgeJetonsExpiresTest {

    private static final Duration CONSERVATION = Duration.ofDays(7);

    @Autowired
    private EntityManager em;
    @Autowired
    private HorlogeReglable horloge;
    @Autowired
    private JetonRafraichissementDepot rafraichissementDepot;
    @Autowired
    private JetonVerificationEmailDepot verificationDepot;
    @Autowired
    private JetonReinitialisationMotDePasseDepot reinitialisationDepot;

    private PurgeJetonsExpires purge;
    private Utilisateur utilisateur;
    private Instant maintenant;

    @BeforeEach
    void preparer() {
        horloge.reinitialiser();
        maintenant = horloge.instant();
        purge = new PurgeJetonsExpires(rafraichissementDepot, verificationDepot, reinitialisationDepot,
                proprietes(), horloge);
        utilisateur = FabriqueComptes.utilisateur(em);
    }

    @Test
    void supprimeLesJetonsExpiresDepuisPlusDeSeptJoursEtGardeLesAutres() {
        // Expirés depuis 8 jours : supprimés
        JetonRafraichissement vieuxRafraichissement = rafraichissement(maintenant.minus(Duration.ofDays(8)));
        verification(maintenant.minus(Duration.ofDays(8)));
        reinitialisation(maintenant.minus(Duration.ofDays(30)));
        // Expirés depuis 6 jours : conservés pour enquête
        JetonRafraichissement recent = rafraichissement(maintenant.minus(Duration.ofDays(6)));
        JetonVerificationEmail verificationRecente = verification(maintenant.minus(Duration.ofDays(6)));
        // Encore valides, même révoqué ou utilisé : conservés (détection de réutilisation)
        JetonRafraichissement revoque = rafraichissement(maintenant.plus(Duration.ofDays(20)));
        revoque.revoquer();
        JetonRafraichissement utilise = rafraichissement(maintenant.plus(Duration.ofDays(1)));
        utilise.marquerUtilise(maintenant);
        JetonReinitialisationMotDePasse valide = reinitialisation(maintenant.plus(Duration.ofMinutes(30)));
        em.flush();

        Bilan bilan = purge.purger();

        assertThat(bilan).isEqualTo(new Bilan(1, 1, 1));
        em.clear();
        assertThat(rafraichissementDepot.findAll()).extracting(JetonRafraichissement::getId)
                .containsExactlyInAnyOrder(recent.getId(), revoque.getId(), utilise.getId())
                .doesNotContain(vieuxRafraichissement.getId());
        assertThat(verificationDepot.findAll()).extracting(JetonVerificationEmail::getId)
                .containsExactly(verificationRecente.getId());
        assertThat(reinitialisationDepot.findAll()).extracting(JetonReinitialisationMotDePasse::getId)
                .containsExactly(valide.getId());
    }

    @Test
    void laPurgeEstIdempotente() {
        rafraichissement(maintenant.minus(Duration.ofDays(10)));
        em.flush();

        assertThat(purge.purger()).isEqualTo(new Bilan(1, 0, 0));
        assertThat(purge.purger()).isEqualTo(new Bilan(0, 0, 0));
    }

    @Test
    void laTacheEstPlanifieeChaqueNuitHeureDeDakar() throws Exception {
        var planification = PurgeJetonsExpires.class.getMethod("purger")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);

        assertThat(planification.cron()).isEqualTo("${boutique.authentification.purge-cron:0 30 3 * * *}");
        assertThat(planification.zone()).isEqualTo("Africa/Dakar");
    }

    private JetonRafraichissement rafraichissement(Instant expiration) {
        return rafraichissementDepot.save(new JetonRafraichissement(utilisateur, empreinte(), expiration,
                UUID.randomUUID(), "41.82.1.1", "Navigateur de test"));
    }

    private JetonVerificationEmail verification(Instant expiration) {
        return verificationDepot.save(new JetonVerificationEmail(utilisateur, empreinte(), expiration));
    }

    private JetonReinitialisationMotDePasse reinitialisation(Instant expiration) {
        return reinitialisationDepot.save(new JetonReinitialisationMotDePasse(utilisateur, empreinte(), expiration));
    }

    private static String empreinte() {
        return GenerateurJetons.empreinte(UUID.randomUUID().toString());
    }

    private static ProprietesAuthentification proprietes() {
        return new ProprietesAuthentification(null, true, "essai", Duration.ofMinutes(15), Duration.ofDays(30),
                Duration.ofHours(12), 5, Duration.ofMinutes(15), Duration.ofHours(24), Duration.ofHours(1),
                CONSERVATION, new ProprietesAuthentification.Cookie("jeton_rafraichissement", "/api/auth", true));
    }
}
