package sn.boutique.noyau.horloge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.support.HorlogeReglable;
import sn.boutique.support.TestDepot;

/** L'audit JPA date les entités avec l'horloge de l'application, et la date relue en base est identique. */
@TestDepot
@Import(HorlogeReglable.Configuration.class)
class AuditHorlogeTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private HorlogeReglable horloge;

    @Test
    void lesDatesDAuditViennentDeLHorlogeEtSurviventALaRelecture() {
        horloge.reinitialiser();
        horloge.avancer(Duration.ofDays(400));
        Instant attendu = horloge.instant();

        Marque marque = em.persistFlushFind(new Marque("Horloge", "horloge"));
        Long id = marque.getId();
        em.clear();
        Marque relue = em.find(Marque.class, id);

        assertThat(relue.getDateCreation()).isEqualTo(attendu);
        assertThat(relue.getDateModification()).isEqualTo(attendu);
    }
}
