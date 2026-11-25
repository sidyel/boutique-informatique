package sn.boutique.authentification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.authentification.config.ProprietesAuthentification;
import sn.boutique.authentification.depot.JetonDeverrouillageDepot;
import sn.boutique.authentification.depot.JetonReinitialisationMotDePasseDepot;
import sn.boutique.authentification.modele.JetonDeverrouillage;
import sn.boutique.authentification.depot.JetonVerificationEmailDepot;
import sn.boutique.authentification.modele.JetonReinitialisationMotDePasse;
import sn.boutique.authentification.modele.JetonVerificationEmail;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.config.ProprietesBoutique;
import sn.boutique.noyau.email.EmailService;

/**
 * E-mails liés au compte. Le jeton est créé dans une courte transaction, validée avant l'envoi :
 * la connexion à la base n'est pas gardée pendant l'échange SMTP (jusqu'à plusieurs tentatives).
 * <p>
 * Liens envoyés (pages du frontend, phase 5) : le jeton est dans l'URL, la page le renvoie à l'API
 * en POST. Ces pages devront servir {@code Referrer-Policy: no-referrer} pour que le jeton ne fuie pas.
 */
@Slf4j
@Service
public class EmailsCompteService {

    static final String CHEMIN_VERIFICATION = "/compte/verifier-email?jeton=";
    static final String CHEMIN_REINITIALISATION = "/compte/nouveau-mot-de-passe?jeton=";
    static final String CHEMIN_DEVERROUILLAGE = "/compte/deverrouiller?jeton=";
    static final String CHEMIN_CONNEXION = "/connexion";
    static final String CHEMIN_MOT_DE_PASSE_OUBLIE = "/mot-de-passe-oublie";

    private final UtilisateurDepot utilisateurDepot;
    private final JetonVerificationEmailDepot verificationDepot;
    private final JetonReinitialisationMotDePasseDepot reinitialisationDepot;
    private final JetonDeverrouillageDepot deverrouillageDepot;
    private final EmailService emailService;
    private final ProprietesAuthentification proprietes;
    private final ProprietesBoutique proprietesBoutique;
    private final Clock horloge;
    private final TransactionTemplate transaction;

    public EmailsCompteService(UtilisateurDepot utilisateurDepot, JetonVerificationEmailDepot verificationDepot,
                               JetonReinitialisationMotDePasseDepot reinitialisationDepot,
                               JetonDeverrouillageDepot deverrouillageDepot, EmailService emailService,
                               ProprietesAuthentification proprietes, ProprietesBoutique proprietesBoutique,
                               Clock horloge, PlatformTransactionManager gestionnaireTransactions) {
        this.utilisateurDepot = utilisateurDepot;
        this.verificationDepot = verificationDepot;
        this.reinitialisationDepot = reinitialisationDepot;
        this.deverrouillageDepot = deverrouillageDepot;
        this.emailService = emailService;
        this.proprietes = proprietes;
        this.proprietesBoutique = proprietesBoutique;
        this.horloge = horloge;
        this.transaction = new TransactionTemplate(gestionnaireTransactions);
    }

    /** Nouveau lien de vérification (les précédents sont invalidés). Rien si l'adresse est déjà vérifiée. */
    public void envoyerVerification(Long utilisateurId) {
        Optional<Envoi> envoi = transaction.execute(statut -> utilisateurDepot.findById(utilisateurId)
                .filter(u -> u.isActif() && !u.isEmailVerifie())
                .map(utilisateur -> {
                    Instant maintenant = horloge.instant();
                    verificationDepot.invaliderPourUtilisateur(utilisateur.getId(), maintenant);
                    String jeton = GenerateurJetons.generer();
                    verificationDepot.save(new JetonVerificationEmail(utilisateur, GenerateurJetons.empreinte(jeton),
                            maintenant.plus(proprietes.dureeJetonVerificationEmail())));
                    return new Envoi(utilisateur.getEmail(), utilisateur.getPrenom(), jeton);
                }));
        envoi.ifPresent(e -> emailService.envoyer(e.email(), "Confirmez votre adresse e-mail", "verification-email",
                Map.of("prenom", e.prenom(),
                        "lien", lien(CHEMIN_VERIFICATION + e.jeton()),
                        "validite", enClair(proprietes.dureeJetonVerificationEmail()))));
    }

    public void envoyerCompteExistant(Long utilisateurId) {
        lireDestinataire(utilisateurId).ifPresent(e -> emailService.envoyer(e.email(),
                "Vous avez déjà un compte", "compte-existant",
                Map.of("prenom", e.prenom(),
                        "lienConnexion", lien(CHEMIN_CONNEXION),
                        "lienMotDePasseOublie", lien(CHEMIN_MOT_DE_PASSE_OUBLIE))));
    }

    /** Nouveau lien de réinitialisation (les précédents sont invalidés). */
    public void envoyerReinitialisation(Long utilisateurId) {
        Optional<Envoi> envoi = transaction.execute(statut -> utilisateurDepot.findById(utilisateurId)
                .filter(Utilisateur::isActif)
                .map(utilisateur -> {
                    Instant maintenant = horloge.instant();
                    reinitialisationDepot.invaliderPourUtilisateur(utilisateur.getId(), maintenant);
                    String jeton = GenerateurJetons.generer();
                    reinitialisationDepot.save(new JetonReinitialisationMotDePasse(utilisateur,
                            GenerateurJetons.empreinte(jeton), maintenant.plus(proprietes.dureeJetonReinitialisation())));
                    return new Envoi(utilisateur.getEmail(), utilisateur.getPrenom(), jeton);
                }));
        envoi.ifPresent(e -> emailService.envoyer(e.email(), "Choisissez un nouveau mot de passe",
                "reinitialisation-mot-de-passe",
                Map.of("prenom", e.prenom(),
                        "lien", lien(CHEMIN_REINITIALISATION + e.jeton()),
                        "validite", enClair(proprietes.dureeJetonReinitialisation()))));
    }

    /**
     * Compte verrouillé depuis une IP : lien de déverrouillage (1 h, usage unique). <strong>Un seul e-mail
     * tant qu'un lien est encore valable</strong> : un attaquant qui verrouille le compte depuis de nombreuses
     * IP ne remplit pas la boîte de la victime.
     */
    public void envoyerDeverrouillage(Long utilisateurId) {
        Optional<Envoi> envoi = transaction.execute(statut -> utilisateurDepot.findById(utilisateurId)
                .filter(Utilisateur::isActif)
                .filter(u -> !deverrouillageDepot.lienValableExiste(u.getId(), horloge.instant()))
                .map(utilisateur -> {
                    String jeton = GenerateurJetons.generer();
                    deverrouillageDepot.save(new JetonDeverrouillage(utilisateur, GenerateurJetons.empreinte(jeton),
                            horloge.instant().plus(proprietes.dureeJetonDeverrouillage())));
                    return new Envoi(utilisateur.getEmail(), utilisateur.getPrenom(), jeton);
                }));
        envoi.ifPresent(e -> emailService.envoyer(e.email(), "Connexion bloquée sur votre compte", "compte-verrouille",
                Map.of("prenom", e.prenom(),
                        "lien", lien(CHEMIN_DEVERROUILLAGE + e.jeton()),
                        "validite", enClair(proprietes.dureeJetonDeverrouillage()),
                        "dureeVerrouillage", enClair(proprietes.dureeVerrouillage()),
                        "lienMotDePasseOublie", lien(CHEMIN_MOT_DE_PASSE_OUBLIE))));
    }

    public void envoyerMotDePasseModifie(Long utilisateurId) {
        lireDestinataire(utilisateurId).ifPresent(e -> emailService.envoyer(e.email(),
                "Votre mot de passe a été modifié", "mot-de-passe-modifie",
                Map.of("prenom", e.prenom(), "lienMotDePasseOublie", lien(CHEMIN_MOT_DE_PASSE_OUBLIE))));
    }

    private Optional<Envoi> lireDestinataire(Long utilisateurId) {
        return transaction.execute(statut -> utilisateurDepot.findById(utilisateurId)
                .map(u -> new Envoi(u.getEmail(), u.getPrenom(), null)));
    }

    private String lien(String chemin) {
        String base = proprietesBoutique.urlSite();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + chemin;
    }

    /** « 24 heures », « 1 heure », « 30 minutes ». */
    static String enClair(Duration duree) {
        if (duree.toMinutesPart() == 0 && duree.toHours() > 0) {
            long heures = duree.toHours();
            return heures + (heures > 1 ? " heures" : " heure");
        }
        long minutes = duree.toMinutes();
        return minutes + (minutes > 1 ? " minutes" : " minute");
    }

    private record Envoi(String email, String prenom, String jeton) {
    }
}
