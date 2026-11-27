package sn.boutique.compte.service;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import sn.boutique.authentification.config.ProprietesAuthentification;
import sn.boutique.authentification.service.EmailsCompteService;
import sn.boutique.compte.evenement.EvenementsCompte.ChangementEmailConfirme;
import sn.boutique.compte.evenement.EvenementsCompte.ChangementEmailDemande;
import sn.boutique.compte.evenement.EvenementsCompte.CommandesRattachees;
import sn.boutique.noyau.config.ProprietesBoutique;
import sn.boutique.noyau.email.EmailService;

/**
 * E-mails de l'espace client, envoyés <strong>après</strong> la validation de la transaction (un rollback n'envoie rien)
 * et en arrière-plan (la réponse HTTP part tout de suite).
 */
@Component
@RequiredArgsConstructor
class EnvoiEmailsEspaceClient {

    static final String CHEMIN_CONFIRMATION_EMAIL = "/compte/confirmer-email?jeton=";
    static final String CHEMIN_SIGNALEMENT = "/compte/signaler-changement?jeton=";
    static final String CHEMIN_MOT_DE_PASSE_OUBLIE = "/mot-de-passe-oublie";
    static final String CHEMIN_COMPTE = "/compte";

    private final EmailService emailService;
    private final ProprietesAuthentification proprietes;
    private final ProprietesBoutique proprietesBoutique;

    /** Deux e-mails : la confirmation vers la NOUVELLE adresse, l'alerte (avec « ce n'est pas moi ») vers l'ANCIENNE. */
    @Async
    @TransactionalEventListener
    void surChangementEmailDemande(ChangementEmailDemande e) {
        emailService.envoyer(e.nouvelEmail(), "Confirmez votre nouvelle adresse e-mail", "changement-email-confirmation",
                Map.of("prenom", e.prenom(),
                        "lien", lien(CHEMIN_CONFIRMATION_EMAIL + e.jetonConfirmation()),
                        "validite", EmailsCompteService.enClair(proprietes.dureeJetonVerificationEmail())));
        emailService.envoyer(e.ancienEmail(), "Demande de changement de votre adresse e-mail", "changement-email-alerte",
                Map.of("prenom", e.prenom(),
                        "nouvelEmail", e.nouvelEmail(),
                        "lienSignalement", lien(CHEMIN_SIGNALEMENT + e.jetonSignalement())));
    }

    /** L'ancienne adresse apprend que le changement a eu lieu. */
    @Async
    @TransactionalEventListener
    void surChangementEmailConfirme(ChangementEmailConfirme e) {
        emailService.envoyer(e.ancienEmail(), "Votre adresse e-mail a été changée", "changement-email-confirme",
                Map.of("prenom", e.prenom(),
                        "nouvelEmail", e.nouvelEmail(),
                        "lienMotDePasseOublie", lien(CHEMIN_MOT_DE_PASSE_OUBLIE)));
    }

    @Async
    @TransactionalEventListener
    void surCommandesRattachees(CommandesRattachees e) {
        List<String> numeros = e.numeros();
        emailService.envoyer(e.email(),
                numeros.size() > 1 ? "Vos commandes sont dans votre compte" : "Votre commande est dans votre compte",
                "commandes-rattachees",
                Map.of("prenom", e.prenom(), "numeros", numeros, "pluriel", numeros.size() > 1,
                        "lienCompte", lien(CHEMIN_COMPTE)));
    }

    private String lien(String chemin) {
        return proprietesBoutique.urlSiteSansBarre() + chemin;
    }
}
