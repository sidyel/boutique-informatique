package sn.boutique.commande.facture;

import java.time.Year;
import java.time.ZoneOffset;
import java.time.Clock;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Numérotation continue et sans trou des factures et des avoirs (ADR 0004 §4, ADR 0008).
 * <p>
 * Une table compteur, pas une séquence : l'incrément est verrouillé (verrou de ligne) jusqu'à la fin de la
 * transaction appelante et annulé avec elle. Si l'émission échoue, aucun numéro n'est consommé. Le verrou sérialise
 * les émissions, négligeable au volume attendu. {@link Propagation#MANDATORY} : appelé hors transaction, ce serait
 * un numéro validé seul, donc un trou possible ; on refuse.
 */
@Service
@RequiredArgsConstructor
public class CompteurDocumentService {

    private final JdbcClient jdbc;
    private final Clock horloge;

    /** Numéro suivant, par exemple {@code FAC-2026-000001}. L'année est celle de l'horloge, en UTC. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String prochainNumero(TypeDocument type) {
        int annee = Year.now(horloge.withZone(ZoneOffset.UTC)).getValue();
        jdbc.sql("insert into compteur_document (type, annee) values (:type, :annee) on conflict do nothing")
                .param("type", type.prefixe()).param("annee", annee).update();
        long numero = jdbc.sql("update compteur_document set dernier_numero = dernier_numero + 1 "
                        + "where type = :type and annee = :annee returning dernier_numero")
                .param("type", type.prefixe()).param("annee", annee).query(Long.class).single();
        return "%s-%d-%06d".formatted(type.prefixe(), annee, numero);
    }
}
