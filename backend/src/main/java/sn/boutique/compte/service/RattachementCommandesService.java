package sn.boutique.compte.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.compte.evenement.EvenementsCompte.CommandesRattachees;
import sn.boutique.compte.modele.Utilisateur;

/**
 * Rattache à un compte les commandes passées en invité (ADR 0008).
 * <p>
 * <strong>Règle de sécurité</strong> : une commande n'est rattachée que si son e-mail est <em>identique</em> (sans tenir
 * compte de la casse) à celui d'un compte dont l'adresse est <em>vérifiée</em>, c'est-à-dire que son titulaire a prouvé
 * l'accès à la boîte mail. <strong>Jamais par le téléphone</strong> : un numéro se saisit sans preuve, et quiconque le
 * taperait verrait les commandes d'un autre. Le téléphone ne servira qu'avec un code envoyé sur ce numéro.
 * <p>
 * Idempotent (une commande déjà rattachée n'est plus touchée), journalisé (table {@code rattachement_commande} et
 * journaux), et le titulaire reçoit un récapitulatif par e-mail. Le lien de suivi invité continue de fonctionner :
 * seul {@code utilisateur_id} change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RattachementCommandesService {

    /** Ce qui a déclenché le rattachement (journal). */
    public enum Declencheur {
        VERIFICATION_EMAIL, CHANGEMENT_EMAIL, REINITIALISATION, CONNEXION
    }

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final ApplicationEventPublisher evenements;

    /** @return le nombre de commandes rattachées (0 si l'adresse n'est pas vérifiée ou s'il n'y a rien à rattacher) */
    @Transactional
    public int rattacher(Utilisateur utilisateur, Declencheur declencheur) {
        if (!utilisateur.isActif() || !utilisateur.isEmailVerifie()) {
            return 0;
        }
        var maintenant = OffsetDateTime.ofInstant(horloge.instant(), ZoneOffset.UTC);
        // Une seule instruction : deux déclencheurs simultanés ne rattachent jamais deux fois la même commande
        List<String> numeros = jdbc.sql("""
                        update commande
                           set utilisateur_id = :utilisateur, date_modification = :date
                         where utilisateur_id is null and lower(email_client) = :email
                     returning numero""")
                .param("utilisateur", utilisateur.getId())
                .param("date", maintenant)
                .param("email", utilisateur.getEmail())
                .query(String.class)
                .list();
        if (numeros.isEmpty()) {
            return 0;
        }
        jdbc.sql("""
                        insert into rattachement_commande (commande_id, utilisateur_id, declencheur, date_rattachement)
                        select id, :utilisateur, :declencheur, :date from commande
                         where numero in (:numeros) and utilisateur_id = :utilisateur
                    on conflict (commande_id) do nothing""")
                .param("utilisateur", utilisateur.getId())
                .param("declencheur", declencheur.name())
                .param("date", maintenant)
                .param("numeros", numeros)
                .update();
        log.info("{} commande(s) passée(s) en invité rattachée(s) au compte {} (déclencheur {}) : {}",
                numeros.size(), utilisateur.getId(), declencheur, numeros);
        evenements.publishEvent(new CommandesRattachees(utilisateur.getId(), utilisateur.getPrenom(),
                utilisateur.getEmail(), numeros));
        return numeros.size();
    }
}
