package sn.boutique.compte.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.authentification.depot.JetonRafraichissementDepot;
import sn.boutique.authentification.depot.JetonReinitialisationMotDePasseDepot;
import sn.boutique.authentification.depot.JetonVerificationEmailDepot;
import sn.boutique.authentification.config.ProprietesAuthentification;
import sn.boutique.authentification.evenement.ReinitialisationDemandee;
import sn.boutique.authentification.service.JetonInvalideException;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.compte.depot.ChangementEmailDepot;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.evenement.EvenementsCompte.ChangementEmailConfirme;
import sn.boutique.compte.evenement.EvenementsCompte.ChangementEmailDemande;
import sn.boutique.compte.modele.ChangementEmail;
import sn.boutique.compte.modele.ChangementEmail.Statut;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.compte.service.RattachementCommandesService.Declencheur;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;
import sn.boutique.noyau.erreur.RegleMetierException;

/**
 * Changement d'adresse e-mail (ADR 0003).
 * <ol>
 *   <li><strong>Demande</strong> : mot de passe exigé. Un lien de confirmation part vers la <em>nouvelle</em> adresse (24 h) ;
 *       l'<em>ancienne</em> reçoit une alerte avec un lien « ce n'est pas moi ». Si la nouvelle adresse appartient déjà à
 *       un compte, la réponse est la même (anti-énumération) et rien n'est envoyé.</li>
 *   <li><strong>Confirmation</strong> : l'adresse du compte change et devient vérifiée (le lien prouve l'accès à la
 *       nouvelle boîte) ; les commandes invité de cette adresse sont rattachées ; l'ancienne adresse est informée.</li>
 *   <li><strong>Signalement « ce n'est pas moi »</strong> : annule la demande, ou rétablit l'ancienne adresse dans les 7 jours
 *       suivant la confirmation ; toutes les sessions sont révoquées et un lien de réinitialisation du mot de passe est
 *       envoyé (le mot de passe est peut-être connu de l'auteur du changement).</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangementEmailService {

    /** Délai pendant lequel un changement déjà confirmé peut encore être signalé comme non sollicité. */
    static final Duration DELAI_SIGNALEMENT = Duration.ofDays(7);

    private final UtilisateurDepot utilisateurDepot;
    private final ChangementEmailDepot changementDepot;
    private final VerificationMotDePasse verification;
    private final RattachementCommandesService rattachement;
    private final JetonRafraichissementDepot rafraichissementDepot;
    private final JetonVerificationEmailDepot verificationEmailDepot;
    private final JetonReinitialisationMotDePasseDepot reinitialisationDepot;
    private final ProprietesAuthentification proprietes;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    @Transactional
    public void demander(Long utilisateurId, String motDePasse, String nouvelEmail) {
        Utilisateur utilisateur = utilisateurDepot.findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Compte introuvable"));
        verification.exiger(utilisateur, motDePasse);

        String nouveau = Utilisateur.normaliserEmail(nouvelEmail);
        if (nouveau.equals(utilisateur.getEmail())) {
            throw new RegleMetierException("EMAIL_IDENTIQUE", "C'est déjà l'adresse e-mail de votre compte.");
        }
        if (utilisateurDepot.emailExiste(nouveau)) {
            // Même réponse que pour une adresse libre : on ne révèle pas qu'un compte existe. Rien n'est envoyé.
            log.info("Changement d'e-mail demandé par le compte {} vers une adresse déjà utilisée : ignoré", utilisateurId);
            return;
        }
        changementDepot.findByUtilisateurIdAndStatut(utilisateurId, Statut.EN_ATTENTE).ifPresent(precedent -> {
            precedent.remplacer();
            changementDepot.saveAndFlush(precedent); // l'index unique n'autorise qu'une demande en attente
        });

        String jetonConfirmation = GenerateurJetons.generer();
        String jetonSignalement = GenerateurJetons.generer();
        Instant expiration = horloge.instant().plus(proprietes.dureeJetonVerificationEmail());
        changementDepot.save(new ChangementEmail(utilisateur, nouveau, GenerateurJetons.empreinte(jetonConfirmation),
                GenerateurJetons.empreinte(jetonSignalement), expiration));
        log.info("Changement d'e-mail demandé par le compte {}", utilisateurId);
        evenements.publishEvent(new ChangementEmailDemande(utilisateur.getPrenom(), utilisateur.getEmail(), nouveau,
                jetonConfirmation, jetonSignalement));
    }

    /** Lien reçu sur la nouvelle adresse. Usage unique. */
    @Transactional
    public void confirmer(String jeton) {
        ChangementEmail changement = changementDepot.trouverParConfirmation(GenerateurJetons.empreinte(jeton))
                .orElseThrow(() -> new JetonInvalideException("jeton de changement d'e-mail inconnu"));
        Instant maintenant = horloge.instant();
        Utilisateur utilisateur = changement.getUtilisateur();
        if (!changement.confirmable(maintenant) || !utilisateur.isActif()) {
            throw new JetonInvalideException("changement d'e-mail expiré, déjà confirmé ou annulé");
        }
        if (utilisateurDepot.emailExiste(changement.getNouvelEmail())) {
            throw new JetonInvalideException("la nouvelle adresse est devenue celle d'un autre compte");
        }
        String ancien = utilisateur.getEmail();
        utilisateur.setEmail(changement.getNouvelEmail());
        utilisateur.setEmailVerifie(true);
        changement.confirmer(maintenant, maintenant.plus(DELAI_SIGNALEMENT));
        // Les liens de vérification envoyés à l'ancienne adresse n'ont plus de sens
        verificationEmailDepot.invaliderPourUtilisateur(utilisateur.getId(), maintenant);
        log.info("Adresse e-mail du compte {} changée", utilisateur.getId());
        rattachement.rattacher(utilisateur, Declencheur.CHANGEMENT_EMAIL);
        evenements.publishEvent(new ChangementEmailConfirme(utilisateur.getPrenom(), ancien, utilisateur.getEmail()));
    }

    /**
     * Lien « ce n'est pas moi » reçu sur l'ancienne adresse. Annule la demande en attente ou rétablit l'ancienne adresse,
     * révoque toutes les sessions et envoie un lien de réinitialisation du mot de passe.
     */
    @Transactional
    public void signaler(String jeton) {
        ChangementEmail changement = changementDepot.trouverParSignalement(GenerateurJetons.empreinte(jeton))
                .orElseThrow(() -> new JetonInvalideException("jeton de signalement inconnu"));
        Instant maintenant = horloge.instant();
        Utilisateur utilisateur = changement.getUtilisateur();
        if (!changement.signalable(maintenant)) {
            throw new JetonInvalideException("signalement expiré ou changement déjà traité");
        }
        if (changement.getStatut() == Statut.EN_ATTENTE) {
            changement.annuler(maintenant);
        } else {
            if (utilisateurDepot.emailExiste(changement.getAncienEmail())) {
                log.error("SÉCURITÉ : impossible de rétablir l'ancienne adresse du compte {} (prise depuis) ; "
                        + "sessions révoquées et réinitialisation envoyée malgré tout", utilisateur.getId());
            } else {
                utilisateur.setEmail(changement.getAncienEmail());
            }
            changement.reverter(maintenant);
        }
        int sessions = rafraichissementDepot.revoquerTousPourUtilisateur(utilisateur.getId());
        reinitialisationDepot.invaliderPourUtilisateur(utilisateur.getId(), maintenant);
        log.warn("SÉCURITÉ : changement d'e-mail signalé comme non sollicité sur le compte {} : {} session(s) révoquée(s), "
                + "réinitialisation du mot de passe envoyée", utilisateur.getId(), sessions);
        evenements.publishEvent(new ReinitialisationDemandee(utilisateur.getId()));
    }
}
