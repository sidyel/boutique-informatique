package sn.boutique.authentification.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.authentification.config.ProprietesAuthentification;
import sn.boutique.authentification.dto.ConnexionRequete;
import sn.boutique.authentification.dto.InscriptionRequete;
import sn.boutique.authentification.evenement.InscriptionAvecEmailExistant;
import sn.boutique.authentification.evenement.InscriptionEffectuee;
import sn.boutique.authentification.service.SessionService.Contexte;
import sn.boutique.authentification.service.SessionService.Session;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Utilisateur;

/**
 * Inscription et connexion.
 * <p>
 * Anti-énumération des comptes (ADR 0003) :
 * <ul>
 *   <li>inscription : réponse identique que l'e-mail soit libre ou déjà pris ; dans le second cas,
 *       le titulaire reçoit un e-mail « vous avez déjà un compte » ;</li>
 *   <li>connexion : même erreur pour un e-mail inconnu, un mot de passe faux, un compte désactivé ou verrouillé,
 *       et un calcul BCrypt est toujours effectué (pas d'écart de temps de réponse exploitable).</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthService {

    /** BCrypt n'utilise que les 72 premiers octets ; Spring refuse au-delà. */
    static final int OCTETS_MAX_MOT_DE_PASSE = 72;

    private final UtilisateurDepot utilisateurDepot;
    private final PasswordEncoder encodeur;
    private final SessionService sessionService;
    private final ApplicationEventPublisher evenements;
    private final ProprietesAuthentification proprietes;
    private final Clock horloge;
    /** Empreinte factice comparée quand l'e-mail est inconnu, pour un temps de réponse constant. */
    private final String empreinteLeurre;

    public AuthService(UtilisateurDepot utilisateurDepot, PasswordEncoder encodeur, SessionService sessionService,
                       ApplicationEventPublisher evenements, ProprietesAuthentification proprietes, Clock horloge) {
        this.utilisateurDepot = utilisateurDepot;
        this.encodeur = encodeur;
        this.sessionService = sessionService;
        this.evenements = evenements;
        this.proprietes = proprietes;
        this.horloge = horloge;
        this.empreinteLeurre = encodeur.encode("leurre-anti-enumeration-" + System.nanoTime());
    }

    @Transactional
    public void inscrire(InscriptionRequete requete) {
        // Le hachage est fait dans tous les cas : même durée de traitement, e-mail libre ou non
        String hash = encodeur.encode(requete.motDePasse());
        var existant = utilisateurDepot.trouverParEmail(requete.email());
        if (existant.isPresent()) {
            log.info("Inscription demandée avec l'e-mail du compte existant {}",
                    existant.get().getId());
            evenements.publishEvent(new InscriptionAvecEmailExistant(existant.get().getId()));
            return;
        }
        Utilisateur utilisateur = new Utilisateur(requete.email(), hash, requete.prenom().strip(), requete.nom().strip());
        utilisateur.setTelephone(requete.telephone());
        utilisateur.definirConsentementMarketing(requete.consentementMarketingDonne(), horloge.instant());
        utilisateurDepot.save(utilisateur);
        evenements.publishEvent(new InscriptionEffectuee(utilisateur.getId()));
    }

    /**
     * Les échecs sont comptés et le verrouillage enregistré même si la connexion échoue :
     * la transaction est validée malgré l'exception.
     */
    @Transactional(noRollbackFor = IdentifiantsInvalidesException.class)
    public Session connecter(ConnexionRequete requete, Contexte contexte) {
        Instant maintenant = horloge.instant();
        String motDePasse = requete.motDePasse();
        boolean longueurAcceptable = motDePasse.getBytes(StandardCharsets.UTF_8).length <= OCTETS_MAX_MOT_DE_PASSE;
        var trouve = utilisateurDepot.trouverParEmail(requete.email());

        if (trouve.isEmpty()) {
            encodeur.matches("leurre", empreinteLeurre);
            throw new IdentifiantsInvalidesException("e-mail inconnu");
        }
        Utilisateur utilisateur = trouve.get();
        if (utilisateur.estVerrouille(maintenant)) {
            encodeur.matches("leurre", empreinteLeurre);
            throw new IdentifiantsInvalidesException("compte " + utilisateur.getId() + " verrouillé");
        }
        boolean correct;
        if (longueurAcceptable) {
            correct = encodeur.matches(motDePasse, utilisateur.getMotDePasseHash());
        } else {
            // Au-delà de 72 octets, BCryptPasswordEncoder lève une exception : on compare le leurre
            encodeur.matches("leurre", empreinteLeurre);
            correct = false;
        }
        if (!correct) {
            enregistrerEchec(utilisateur, maintenant);
            throw new IdentifiantsInvalidesException("mot de passe incorrect pour le compte " + utilisateur.getId());
        }
        if (!utilisateur.isActif()) {
            throw new IdentifiantsInvalidesException("compte " + utilisateur.getId() + " désactivé");
        }
        utilisateur.setEchecsConnexion(0);
        utilisateur.setVerrouilleJusqua(null);
        return sessionService.ouvrir(utilisateur, contexte);
    }

    private void enregistrerEchec(Utilisateur utilisateur, Instant maintenant) {
        int echecs = utilisateur.getEchecsConnexion() + 1;
        if (echecs >= proprietes.echecsAvantVerrouillage()) {
            utilisateur.setVerrouilleJusqua(maintenant.plus(proprietes.dureeVerrouillage()));
            utilisateur.setEchecsConnexion(0);
            log.warn("SÉCURITÉ : compte {} verrouillé jusqu'à {} après {} échecs de connexion",
                    utilisateur.getId(), utilisateur.getVerrouilleJusqua(), echecs);
        } else {
            utilisateur.setEchecsConnexion(echecs);
        }
    }
}
