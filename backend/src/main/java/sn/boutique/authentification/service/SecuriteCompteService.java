package sn.boutique.authentification.service;

import java.time.Clock;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.authentification.depot.JetonRafraichissementDepot;
import sn.boutique.authentification.depot.JetonReinitialisationMotDePasseDepot;
import sn.boutique.authentification.depot.JetonVerificationEmailDepot;
import sn.boutique.authentification.evenement.MotDePasseModifie;
import sn.boutique.authentification.evenement.ReinitialisationDemandee;
import sn.boutique.authentification.evenement.RenvoiVerificationDemande;
import sn.boutique.authentification.modele.JetonReinitialisationMotDePasse;
import sn.boutique.authentification.modele.JetonVerificationEmail;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Utilisateur;

/**
 * Vérification de l'e-mail et mot de passe oublié (ADR 0003).
 * <p>
 * Les demandes ({@link #demanderRenvoiVerification}, {@link #demanderReinitialisation}) répondent toujours
 * de la même façon : l'e-mail n'est envoyé que si le compte existe, en arrière-plan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecuriteCompteService {

    private final UtilisateurDepot utilisateurDepot;
    private final JetonVerificationEmailDepot verificationDepot;
    private final JetonReinitialisationMotDePasseDepot reinitialisationDepot;
    private final JetonRafraichissementDepot rafraichissementDepot;
    private final PasswordEncoder encodeur;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    /** Valide le lien reçu par e-mail. Le JWT en cours garde emailVerifie=false jusqu'au prochain rafraîchissement. */
    @Transactional
    public void verifierEmail(String jeton) {
        JetonVerificationEmail trouve = verificationDepot.findByEmpreinte(GenerateurJetons.empreinte(jeton))
                .orElseThrow(() -> new JetonInvalideException("jeton de vérification inconnu"));
        Instant maintenant = horloge.instant();
        if (!trouve.estUtilisable(maintenant)) {
            throw new JetonInvalideException("jeton de vérification expiré ou déjà utilisé");
        }
        trouve.marquerUtilise(maintenant);
        trouve.getUtilisateur().setEmailVerifie(true);
        log.info("Adresse e-mail du compte {} vérifiée", trouve.getUtilisateur().getId());
    }

    @Transactional(readOnly = true)
    public void demanderRenvoiVerification(String email) {
        utilisateurDepot.trouverParEmail(email)
                .filter(u -> u.isActif() && !u.isEmailVerifie())
                .ifPresent(u -> evenements.publishEvent(new RenvoiVerificationDemande(u.getId())));
    }

    @Transactional(readOnly = true)
    public void demanderReinitialisation(String email) {
        utilisateurDepot.trouverParEmail(email)
                .filter(Utilisateur::isActif)
                .ifPresent(u -> evenements.publishEvent(new ReinitialisationDemandee(u.getId())));
    }

    /**
     * Nouveau mot de passe choisi depuis le lien reçu par e-mail. Effets : verrouillage levé, adresse
     * considérée comme vérifiée (le lien prouve l'accès à la boîte mail), autres liens invalidés,
     * <strong>toutes les sessions révoquées</strong>, e-mail d'alerte envoyé au titulaire.
     */
    @Transactional
    public void reinitialiserMotDePasse(String jeton, String nouveauMotDePasse) {
        JetonReinitialisationMotDePasse trouve = reinitialisationDepot.findByEmpreinte(GenerateurJetons.empreinte(jeton))
                .orElseThrow(() -> new JetonInvalideException("jeton de réinitialisation inconnu"));
        Instant maintenant = horloge.instant();
        Utilisateur utilisateur = trouve.getUtilisateur();
        if (!trouve.estUtilisable(maintenant) || !utilisateur.isActif()) {
            throw new JetonInvalideException("jeton de réinitialisation expiré, utilisé ou compte désactivé");
        }
        trouve.marquerUtilise(maintenant);
        utilisateur.setMotDePasseHash(encodeur.encode(nouveauMotDePasse));
        utilisateur.setEchecsConnexion(0);
        utilisateur.setVerrouilleJusqua(null);
        utilisateur.setEmailVerifie(true);
        reinitialisationDepot.invaliderPourUtilisateur(utilisateur.getId(), maintenant);
        int sessions = rafraichissementDepot.revoquerTousPourUtilisateur(utilisateur.getId());
        log.info("Mot de passe du compte {} réinitialisé : {} session(s) révoquée(s)", utilisateur.getId(), sessions);
        evenements.publishEvent(new MotDePasseModifie(utilisateur.getId()));
    }
}
