package sn.boutique.administration.utilisateur;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import sn.boutique.administration.utilisateur.UtilisateurInterneService.InvitationEnvoyee;
import sn.boutique.administration.utilisateur.UtilisateurInterneService.SecondFacteurReinitialise;
import sn.boutique.authentification.config.ProprietesSecondFacteur;
import sn.boutique.authentification.service.EmailsCompteService;
import sn.boutique.noyau.config.ProprietesBoutique;
import sn.boutique.noyau.email.EmailService;

/** E-mails des comptes de l'équipe, envoyés après validation de la transaction et en arrière-plan. */
@Component
@RequiredArgsConstructor
class EnvoiEmailsAdministration {

    /** Page du frontend qui lit le jeton et le renvoie à {@code /api/auth/reinitialiser} (comme le mot de passe oublié). */
    static final String CHEMIN_INVITATION = "/admin/invitation?jeton=";
    static final String CHEMIN_CONNEXION = "/admin/connexion";

    private final EmailService emailService;
    private final ProprietesBoutique proprietesBoutique;
    private final ProprietesSecondFacteur proprietes;

    @Async
    @TransactionalEventListener
    void surInvitation(InvitationEnvoyee e) {
        emailService.envoyer(e.email(), "Votre accès à l'administration de la boutique", "invitation-equipe",
                Map.of("prenom", e.prenom(), "role", libelle(e.role()),
                        "lien", lien(CHEMIN_INVITATION + e.jeton()),
                        "validite", EmailsCompteService.enClair(proprietes.dureeInvitation())));
    }

    @Async
    @TransactionalEventListener
    void surSecondFacteurReinitialise(SecondFacteurReinitialise e) {
        emailService.envoyer(e.email(), "Votre double authentification a été réinitialisée", "second-facteur-reinitialise",
                Map.of("prenom", e.prenom(), "lienConnexion", lien(CHEMIN_CONNEXION)));
    }

    private String lien(String chemin) {
        return proprietesBoutique.urlSiteSansBarre() + chemin;
    }

    private static String libelle(sn.boutique.compte.modele.Role role) {
        return switch (role) {
            case SERVICE_CLIENT -> "Service client";
            case GESTIONNAIRE_STOCK -> "Gestionnaire de stock";
            case ADMIN -> "Administrateur";
            case SUPER_ADMIN -> "Super-administrateur";
            case CLIENT -> "Client";
        };
    }
}
