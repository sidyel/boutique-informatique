package sn.boutique.compte.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.evenement.EvenementsCompte.CompteSupprime;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;
import sn.boutique.noyau.limitation.LimiteurDebit;

/**
 * Suppression du compte (droit à l'effacement) : anonymisation partielle selon l'ADR 0004 §3.
 * <p>
 * Tout se passe dans <strong>une seule transaction</strong> : si une étape échoue, rien n'est changé.
 * <ul>
 *   <li><strong>Refusée</strong> tant qu'une commande n'est pas terminée (livrée, annulée ou remboursée), qu'un
 *   remboursement est à faire ou qu'une demande après-vente est ouverte.</li>
 *   <li><strong>Gardé</strong> : numéro, dates, statuts, lignes, montants, paiements, factures et avoirs ; coordonnées
 *   du client seulement si une facture a été émise (elle les exige).</li>
 *   <li><strong>Effacé</strong> : adresse IP, navigateur, identifiants publicitaires, UTM, pages d'arrivée, corps
 *   bruts des webhooks, lien de suivi ; coordonnées des commandes sans facture ; adresses, panier, sessions et
 *   jetons ; alertes de produit ; compteurs de limitation (Redis et mémoire).</li>
 *   <li><strong>Anonymisé</strong> : avis (« Client vérifié » ou « Client »), messages SAV (« Client supprimé »),
 *   historique de consentement et de changements d'e-mail (conservés sans identité), auteur des historiques de
 *   statut.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuppressionCompteService {

    public static final String MOT_CONFIRMATION = "SUPPRIMER";
    static final String CLIENT_SUPPRIME = "Client supprimé";
    static final String EMAIL_ANONYME = "supprime@invalide.local";

    private final UtilisateurDepot utilisateurDepot;
    private final VerificationMotDePasse verification;
    private final JdbcClient jdbc;
    private final EntityManager entityManager;
    private final LimiteurDebit limiteur;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    @Transactional
    public void supprimer(Long id, String motDePasse, String confirmation) {
        Utilisateur utilisateur = utilisateurDepot.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Compte", id));
        if (utilisateur.getRole() != Role.CLIENT) {
            throw new RegleMetierException("COMPTE_NON_SUPPRIMABLE",
                    "Ce compte appartient à l'équipe : demandez sa suppression à un administrateur.");
        }
        if (confirmation == null || !MOT_CONFIRMATION.equals(confirmation.strip().toUpperCase(Locale.ROOT))) {
            throw new RegleMetierException("CONFIRMATION_INCORRECTE", "Saisissez le mot SUPPRIMER pour confirmer.");
        }
        verification.exiger(utilisateur, motDePasse);
        refuserSiCommandeEnCours(id);

        String prenom = utilisateur.getPrenom();
        String emailActuel = utilisateur.getEmail();
        Set<String> emails = emailsDuCompte(utilisateur);
        Set<String> empreintes = empreintesAOublier(id, emails);
        Timestamp maintenant = Timestamp.from(horloge.instant());

        // Commandes : tout le suivi publicitaire et technique part ; les coordonnées ne restent que si une facture existe
        jdbc.sql("""
                update commande set adresse_ip = null, user_agent = null, fbp = null, fbc = null, gclid = null,
                       fbclid = null, utm_source = null, utm_medium = null, utm_campagne = null, utm_contenu = null,
                       utm_terme = null, page_arrivee = null, referent = null, premiere_source = null,
                       premier_medium = null, premiere_campagne = null, premiere_page_arrivee = null,
                       empreinte_jeton_suivi = null, anonymisee_le = :maintenant, date_modification = :maintenant
                 where utilisateur_id = :u""")
                .param("maintenant", maintenant).param("u", id).update();
        jdbc.sql("""
                update commande c set livraison_nom_complet = :supprime, livraison_telephone = null,
                       livraison_ville = null, livraison_quartier = null, livraison_adresse_ligne = null,
                       livraison_point_de_repere = null, email_client = null, telephone_client = null, nom_client = null
                 where c.utilisateur_id = :u and not exists (select 1 from facture f where f.commande_id = c.id)""")
                .param("supprime", CLIENT_SUPPRIME).param("u", id).update();
        // Commandes facturées : le point de repère ne reste que s'il figure sur la facture
        jdbc.sql("""
                update commande c set livraison_point_de_repere = null
                 where c.utilisateur_id = :u and c.livraison_point_de_repere is not null
                   and not exists (select 1 from facture f where f.commande_id = c.id
                                    and position(c.livraison_point_de_repere in coalesce(f.client_adresse, '')) > 0)""")
                .param("u", id).update();
        jdbc.sql("""
                update paiement set donnees_brutes = null
                 where commande_id in (select id from commande where utilisateur_id = :u)""").param("u", id).update();
        jdbc.sql("""
                update historique_statut_commande set auteur = :supprime
                 where commande_id in (select id from commande where utilisateur_id = :u)
                   and lower(auteur) in (:emails)""")
                .param("supprime", CLIENT_SUPPRIME).param("u", id).param("emails", List.copyOf(emails)).update();

        // Avis (restent publiés), messages SAV, historique de changement d'e-mail
        jdbc.sql("""
                update avis set nom_auteur = case when achat_verifie then 'Client vérifié' else 'Client' end
                 where utilisateur_id = :u""").param("u", id).update();
        jdbc.sql("update message_sav set nom_auteur = :supprime where auteur_id = :u")
                .param("supprime", CLIENT_SUPPRIME).param("u", id).update();
        jdbc.sql("""
                update changement_email set ancien_email = :anonyme, nouvel_email = :anonyme,
                       statut = case when statut = 'EN_ATTENTE' then 'ANNULE' else statut end
                 where utilisateur_id = :u""").param("anonyme", EMAIL_ANONYME).param("u", id).update();

        // Données hors du compte, rattachées à l'e-mail ou à l'utilisateur
        jdbc.sql("delete from alerte_produit where email in (:emails)").param("emails", List.copyOf(emails)).update();
        jdbc.sql("delete from echec_connexion where empreinte_email in (:empreintes)")
                .param("empreintes", emails.stream().map(GenerateurJetons::empreinte).toList()).update();
        jdbc.sql("delete from cle_idempotence where portee = :portee").param("portee", "u:" + id).update();

        // Le compte : adresses, panier, sessions et jetons partent en cascade ; consentements et changements
        // d'e-mail restent, sans identité (clé étrangère SET NULL)
        entityManager.flush();
        entityManager.detach(utilisateur);
        jdbc.sql("delete from utilisateur where id = :u").param("u", id).update();

        limiteur.oublier(empreintes);
        log.info("Compte {} supprimé et anonymisé", id);
        evenements.publishEvent(new CompteSupprime(prenom, emailActuel));
    }

    private void refuserSiCommandeEnCours(Long id) {
        List<String> numeros = jdbc.sql("""
                select c.numero from commande c
                 where c.utilisateur_id = :u
                   and (c.statut not in ('LIVREE', 'ANNULEE', 'REMBOURSEE')
                        or exists (select 1 from paiement p where p.commande_id = c.id and p.statut = 'A_REMBOURSER')
                        or exists (select 1 from demande_sav d where d.commande_id = c.id
                                    and d.statut not in ('RESOLUE', 'REFUSEE')))
                 order by c.date_creation""").param("u", id).query(String.class).list();
        if (!numeros.isEmpty()) {
            throw new RegleMetierException("COMMANDE_EN_COURS",
                    "Votre compte ne peut pas être supprimé tant que ce n'est pas terminé pour : "
                            + String.join(", ", numeros)
                            + ". Chaque commande doit être livrée, annulée ou remboursée, sans paiement à rembourser ni "
                            + "demande après-vente ouverte. Une question ? Contactez-nous.");
        }
    }

    /** Adresse actuelle et anciennes adresses (changements d'e-mail), en minuscules. */
    private Set<String> emailsDuCompte(Utilisateur utilisateur) {
        Set<String> emails = new LinkedHashSet<>();
        emails.add(utilisateur.getEmail());
        emails.addAll(jdbc.sql("select ancien_email from changement_email where utilisateur_id = :u")
                .param("u", utilisateur.getId()).query(String.class).list());
        emails.addAll(jdbc.sql("select nouvel_email from changement_email where utilisateur_id = :u")
                .param("u", utilisateur.getId()).query(String.class).list());
        emails.removeIf(e -> e == null || e.isBlank());
        return emails;
    }

    /** Fragments de clés de limitation à effacer : empreintes des e-mails et des téléphones, compteur du compte. */
    private Set<String> empreintesAOublier(Long id, Set<String> emails) {
        Set<String> fragments = new LinkedHashSet<>();
        emails.forEach(email -> fragments.add(GenerateurJetons.empreinte(email)));
        Set<String> telephones = new LinkedHashSet<>();
        telephones.addAll(jdbc.sql("select telephone from utilisateur where id = :u and telephone is not null")
                .param("u", id).query(String.class).list());
        telephones.addAll(jdbc.sql("select telephone from adresse where utilisateur_id = :u").param("u", id)
                .query(String.class).list());
        telephones.addAll(jdbc.sql("""
                select telephone_client from commande where utilisateur_id = :u and telephone_client is not null
                union select livraison_telephone from commande where utilisateur_id = :u and livraison_telephone is not null""")
                .param("u", id).query(String.class).list());
        telephones.forEach(t -> fragments.add(GenerateurJetons.empreinte(t)));
        fragments.add("mot-de-passe-actuel:" + id);
        return fragments;
    }
}
