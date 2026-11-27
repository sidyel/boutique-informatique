package sn.boutique.compte.service;

import java.time.Clock;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.authentification.depot.JetonRafraichissementDepot;
import sn.boutique.authentification.depot.JetonReinitialisationMotDePasseDepot;
import sn.boutique.authentification.evenement.MotDePasseModifie;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.dto.ChangementMotDePasseRequete;
import sn.boutique.compte.dto.ModificationProfilRequete;
import sn.boutique.compte.dto.ProfilReponse;
import sn.boutique.compte.modele.SourceConsentement;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/** Profil, mot de passe et consentement du client connecté. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompteService {

    private final UtilisateurDepot utilisateurDepot;
    private final VerificationMotDePasse verification;
    private final ConsentementMarketingService consentement;
    private final PasswordEncoder encodeur;
    private final JetonRafraichissementDepot rafraichissementDepot;
    private final JetonReinitialisationMotDePasseDepot reinitialisationDepot;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    @Transactional(readOnly = true)
    public ProfilReponse profil(Long id) {
        return ProfilReponse.de(lire(id));
    }

    @Transactional
    public ProfilReponse modifierProfil(Long id, ModificationProfilRequete requete) {
        Utilisateur u = lire(id);
        u.setPrenom(requete.prenom().strip());
        u.setNom(requete.nom().strip());
        // Normalisé en E.164 (+221…) comme partout ailleurs ; vide : téléphone effacé
        u.setTelephone(requete.telephone() == null || requete.telephone().isBlank() ? null : requete.telephone());
        return ProfilReponse.de(u);
    }

    /**
     * Changement de mot de passe : l'ancien est exigé ; <strong>les autres sessions sont révoquées</strong> (un appareil
     * volé ou oublié est déconnecté) mais la session courante reste ouverte ; un e-mail d'alerte est envoyé.
     *
     * @param sessionCourante famille de la session (revendication {@code sid} du jeton) ; null : toutes les sessions
     *                        sont révoquées
     */
    @Transactional
    public void changerMotDePasse(Long id, UUID sessionCourante, ChangementMotDePasseRequete requete) {
        Utilisateur u = lire(id);
        verification.exiger(u, requete.motDePasseActuel());
        if (encodeur.matches(requete.nouveauMotDePasse(), u.getMotDePasseHash())) {
            throw new RegleMetierException("MOT_DE_PASSE_IDENTIQUE", "Choisissez un mot de passe différent de l'actuel.");
        }
        u.setMotDePasseHash(encodeur.encode(requete.nouveauMotDePasse()));
        // Les mises à jour de masse enregistrent d'abord l'entité, puis vident le contexte de persistance
        reinitialisationDepot.invaliderPourUtilisateur(id, horloge.instant());
        int revoquees = sessionCourante == null
                ? rafraichissementDepot.revoquerTousPourUtilisateur(id)
                : rafraichissementDepot.revoquerAutresFamilles(id, sessionCourante);
        log.info("Mot de passe du compte {} changé : {} session(s) révoquée(s)", id, revoquees);
        evenements.publishEvent(new MotDePasseModifie(id, sessionCourante != null));
    }

    @Transactional
    public ProfilReponse definirConsentementMarketing(Long id, boolean accepte) {
        Utilisateur u = lire(id);
        consentement.definir(u, accepte, SourceConsentement.COMPTE);
        return ProfilReponse.de(u);
    }

    private Utilisateur lire(Long id) {
        return utilisateurDepot.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Compte introuvable"));
    }
}
