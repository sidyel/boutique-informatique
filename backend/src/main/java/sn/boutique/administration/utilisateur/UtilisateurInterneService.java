package sn.boutique.administration.utilisateur;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.administration.journal.JournalAdministration;
import sn.boutique.administration.securite.ReauthentificationService;
import sn.boutique.authentification.config.ProprietesSecondFacteur;
import sn.boutique.authentification.depot.CodeSecoursDepot;
import sn.boutique.authentification.depot.EtapeConnexionDepot;
import sn.boutique.authentification.depot.JetonRafraichissementDepot;
import sn.boutique.authentification.depot.JetonReinitialisationMotDePasseDepot;
import sn.boutique.authentification.modele.JetonReinitialisationMotDePasse;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.erreur.ConflitException;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Comptes de l'équipe (ADR 0009 §7) : invitation, rôle, désactivation, réinitialisation du second facteur. Réservé aux
 * {@code SUPER_ADMIN}, avec réauthentification (code TOTP récent). Chaque action est journalisée dans la même transaction.
 * <p>
 * Un changement de rôle, une désactivation ou une réinitialisation du second facteur <strong>révoquent les sessions</strong> du
 * compte, ferment sa fenêtre de réauthentification et annulent ses étapes de connexion en cours.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UtilisateurInterneService {

    private static final SecureRandom ALEATOIRE = new SecureRandom();

    private final UtilisateurDepot utilisateurs;
    private final JetonRafraichissementDepot sessions;
    private final JetonReinitialisationMotDePasseDepot jetonsMotDePasse;
    private final EtapeConnexionDepot etapes;
    private final CodeSecoursDepot codesSecours;
    private final ReauthentificationService reauthentification;
    private final JournalAdministration journal;
    private final ApplicationEventPublisher evenements;
    private final ProprietesSecondFacteur proprietes;
    private final Clock horloge;

    /** Un compte invité : lien envoyé par e-mail (jeton en clair, jamais stocké). */
    public record InvitationEnvoyee(Long utilisateurId, String email, String prenom, Role role, String jeton) {
    }

    /** Le second facteur d'un compte vient d'être réinitialisé par un SUPER_ADMIN. */
    public record SecondFacteurReinitialise(Long utilisateurId, String email, String prenom) {
    }

    @Transactional(readOnly = true)
    public List<Utilisateur> lister() {
        return utilisateurs.findByRoleNotOrderByDateCreationAsc(Role.CLIENT);
    }

    @Transactional(readOnly = true)
    public long codesSecoursRestants(Long utilisateurId) {
        return codesSecours.compterRestants(utilisateurId);
    }

    @Transactional
    public Utilisateur inviter(String email, String prenom, String nom, Role role) {
        exigerRoleInterne(role);
        if (utilisateurs.emailExiste(email)) {
            throw new ConflitException("EMAIL_DEJA_UTILISE", "Un compte existe déjà avec cette adresse e-mail.");
        }
        // Mot de passe inutilisable jusqu'à ce que l'invité choisisse le sien avec le lien reçu par e-mail
        Utilisateur invite = new Utilisateur(email, Utilisateur.PREFIXE_INVITATION + aleatoire(), prenom.strip(), nom.strip());
        invite.setRole(role);
        invite.setEmailVerifie(true);
        utilisateurs.save(invite);
        String jeton = creerLienInvitation(invite);
        journal.enregistrer("UTILISATEUR_INVITE", "Utilisateur", invite.getId(), null, Map.of("role", role.name()));
        evenements.publishEvent(new InvitationEnvoyee(invite.getId(), invite.getEmail(), invite.getPrenom(), role, jeton));
        return invite;
    }

    @Transactional
    public void renvoyerInvitation(Long id) {
        // Invalider avant de charger : la mise à jour de masse vide le contexte de persistance
        jetonsMotDePasse.invaliderPourUtilisateur(id, horloge.instant());
        Utilisateur invite = charger(id);
        if (!invite.invitationEnAttente()) {
            throw new RegleMetierException("INVITATION_DEJA_ACCEPTEE", "Ce compte a déjà choisi son mot de passe.");
        }
        String jeton = creerLienInvitation(invite);
        journal.enregistrer("INVITATION_RENVOYEE", "Utilisateur", id, null, null);
        evenements.publishEvent(new InvitationEnvoyee(id, invite.getEmail(), invite.getPrenom(), invite.getRole(), jeton));
    }

    @Transactional
    public Utilisateur changerRole(Long acteurId, Long id, Role role) {
        exigerRoleInterne(role);
        coupeSessions(id);
        Utilisateur cible = charger(id);
        exigerAutre(acteurId, id, "Vous ne pouvez pas changer votre propre rôle.");
        Role ancien = cible.getRole();
        if (ancien == role) {
            return cible;
        }
        if (ancien == Role.SUPER_ADMIN && cible.isActif()) {
            exigerUnAutreSuperAdminActif(id);
        }
        cible.setRole(role);
        journal.enregistrer("UTILISATEUR_ROLE_CHANGE", "Utilisateur", id, Map.of("role", ancien.name()),
                Map.of("role", role.name()));
        return cible;
    }

    @Transactional
    public Utilisateur desactiver(Long acteurId, Long id) {
        coupeSessions(id);
        Utilisateur cible = charger(id);
        exigerAutre(acteurId, id, "Vous ne pouvez pas désactiver votre propre compte.");
        if (cible.isActif()) {
            if (cible.getRole() == Role.SUPER_ADMIN) {
                exigerUnAutreSuperAdminActif(id);
            }
            cible.setActif(false);
            journal.enregistrer("UTILISATEUR_DESACTIVE", "Utilisateur", id, Map.of("actif", true), Map.of("actif", false));
        }
        return cible;
    }

    @Transactional
    public Utilisateur activer(Long id) {
        Utilisateur cible = charger(id);
        exigerRoleInterne(cible.getRole());
        if (!cible.isActif()) {
            cible.setActif(true);
            journal.enregistrer("UTILISATEUR_REACTIVE", "Utilisateur", id, Map.of("actif", false), Map.of("actif", true));
        }
        return cible;
    }

    /** Efface secret et codes de secours : à la prochaine connexion, le titulaire s'enrôle de nouveau. */
    @Transactional
    public Utilisateur reinitialiserSecondFacteur(Long acteurId, Long id) {
        coupeSessions(id);
        codesSecours.supprimerPourUtilisateur(id);
        Utilisateur cible = charger(id);
        exigerAutre(acteurId, id, "Pour votre propre compte, utilisez « Mon compte » : vous ne pouvez pas réinitialiser "
                + "vous-même le second facteur qui vous a ouvert cette session.");
        Map<String, Object> avant = new LinkedHashMap<>();
        avant.put("secondFacteurActif", cible.secondFacteurActif());
        cible.desactiverSecondFacteur();
        journal.enregistrer("SECOND_FACTEUR_REINITIALISE", "Utilisateur", id, avant, Map.of("secondFacteurActif", false));
        evenements.publishEvent(new SecondFacteurReinitialise(id, cible.getEmail(), cible.getPrenom()));
        return cible;
    }

    // ------------------------------------------------------------------------------------------------

    private void coupeSessions(Long id) {
        Instant maintenant = horloge.instant();
        // Mises à jour de masse : avant tout chargement d'entité (elles vident le contexte de persistance)
        sessions.revoquerTousPourUtilisateur(id);
        etapes.invaliderPourUtilisateur(id, maintenant);
        reauthentification.fermerPourUtilisateur(id);
    }

    private String creerLienInvitation(Utilisateur invite) {
        String jeton = GenerateurJetons.generer();
        jetonsMotDePasse.save(new JetonReinitialisationMotDePasse(invite, GenerateurJetons.empreinte(jeton),
                horloge.instant().plus(proprietes.dureeInvitation())));
        return jeton;
    }

    private Utilisateur charger(Long id) {
        Utilisateur u = utilisateurs.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", id));
        if (!u.getRole().estInterne()) {
            // Un client n'est jamais géré ici : même réponse que si le compte n'existait pas
            throw new RessourceIntrouvableException("Utilisateur", id);
        }
        return u;
    }

    private static void exigerRoleInterne(Role role) {
        if (role == null || !role.estInterne()) {
            throw new RegleMetierException("ROLE_NON_INTERNE", "Choisissez un rôle de l'équipe.");
        }
    }

    private static void exigerAutre(Long acteurId, Long cibleId, String message) {
        if (acteurId != null && acteurId.equals(cibleId)) {
            throw new RegleMetierException("ACTION_SUR_SOI_REFUSEE", message);
        }
    }

    private void exigerUnAutreSuperAdminActif(Long exclu) {
        if (utilisateurs.compterActifsParRoleSauf(Role.SUPER_ADMIN, exclu) == 0) {
            throw new RegleMetierException("DERNIER_SUPER_ADMIN",
                    "C'est le dernier super-administrateur actif : l'application doit toujours en avoir un.");
        }
    }

    private static String aleatoire() {
        byte[] octets = new byte[24];
        ALEATOIRE.nextBytes(octets);
        return HexFormat.of().formatHex(octets);
    }
}
