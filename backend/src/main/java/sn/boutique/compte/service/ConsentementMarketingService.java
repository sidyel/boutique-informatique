package sn.boutique.compte.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.compte.modele.SourceConsentement;
import sn.boutique.compte.modele.Utilisateur;

/**
 * Consentement marketing : chaque décision (acceptation <em>ou</em> retrait) est datée et sa source conservée, dans
 * l'historique (preuve) et sur le compte (dernier état). Le retrait est aussi simple que l'acceptation : un seul appel,
 * sans mot de passe ni confirmation.
 */
@Service
@RequiredArgsConstructor
public class ConsentementMarketingService {

    private final JdbcClient jdbc;
    private final Clock horloge;

    /** Enregistre la décision si elle change l'état (ou si aucune décision n'a encore été enregistrée). */
    @Transactional
    public void definir(Utilisateur utilisateur, boolean consentement, SourceConsentement source) {
        boolean premiere = utilisateur.getDateConsentement() == null;
        if (!premiere && utilisateur.isConsentementMarketing() == consentement) {
            return;
        }
        var maintenant = horloge.instant();
        utilisateur.definirConsentementMarketing(consentement, maintenant, source);
        jdbc.sql("""
                        insert into historique_consentement (utilisateur_id, consentement, source, date_decision)
                        values (:utilisateur, :consentement, :source, :date)""")
                .param("utilisateur", utilisateur.getId())
                .param("consentement", consentement)
                .param("source", source.name())
                .param("date", OffsetDateTime.ofInstant(maintenant, ZoneOffset.UTC))
                .update();
    }
}
