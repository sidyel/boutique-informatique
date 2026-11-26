package sn.boutique.marketing.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Utilisation d'un code promo réservée à la création de la commande, rendue si elle est annulée ou expire.
 * <p>
 * Le compteur est incrémenté par un UPDATE conditionnel : deux clients qui prennent en même temps la dernière
 * utilisation ne peuvent pas passer tous les deux (le second attend le premier, puis ne trouve plus de place).
 * L'UPDATE garde aussi le verrou de la ligne jusqu'à la fin de la transaction, ce qui sérialise le contrôle du
 * plafond par client.
 */
@Service
@RequiredArgsConstructor
public class ReservationCodePromo {

    private final JdbcClient jdbc;
    private final Clock horloge;

    /** @return faux si le code est inactif ou a atteint son plafond d'utilisations */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reserver(String code) {
        return jdbc.sql("""
                        update code_promo
                           set nombre_utilisations = nombre_utilisations + 1, date_modification = :maintenant
                         where code = :code and actif
                           and (utilisations_max is null or nombre_utilisations < utilisations_max)
                        """)
                .param("code", code)
                .param("maintenant", maintenant())
                .update() == 1;
    }

    /** Rend l'utilisation (commande annulée ou expirée). Jamais en dessous de zéro. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void liberer(String code) {
        jdbc.sql("""
                        update code_promo
                           set nombre_utilisations = greatest(0, nombre_utilisations - 1), date_modification = :maintenant
                         where code = :code
                        """)
                .param("code", code)
                .param("maintenant", maintenant())
                .update();
    }

    private OffsetDateTime maintenant() {
        return OffsetDateTime.ofInstant(horloge.instant(), ZoneOffset.UTC);
    }
}
