package sn.boutique.authentification.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.authentification.config.ProprietesAuthentification;
import sn.boutique.authentification.depot.EchecConnexionDepot;
import sn.boutique.authentification.evenement.CompteVerrouille;
import sn.boutique.authentification.securite.GenerateurJetons;
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
 *       et un calcul BCrypt est toujours effectué ;</li>
 *   <li>échecs (relecture de sécurité, M1) : comptés par couple (empreinte de l'e-mail SAISI, IP), par la même
 *       écriture que le compte existe ou non. Aucun écart de temps de réponse ne révèle un compte.</li>
 * </ul>
 * Verrouillage (I1, ADR 0003 §8) : après {@code echecs-avant-verrouillage} échecs depuis une IP, le couple
 * (e-mail, IP) est verrouillé ; la même adresse reste utilisable depuis une autre IP. Au verrouillage d'un
 * compte existant, son titulaire reçoit un lien de déverrouillage.
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
    private final EchecConnexionDepot echecs;
    private final Clock horloge;
    /** Empreinte factice comparée quand l'e-mail est inconnu, pour un temps de réponse constant. */
    private final String empreinteLeurre;

    public AuthService(UtilisateurDepot utilisateurDepot, PasswordEncoder encodeur, SessionService sessionService,
                       ApplicationEventPublisher evenements, ProprietesAuthentification proprietes,
                       EchecConnexionDepot echecs, Clock horloge) {
        this.utilisateurDepot = utilisateurDepot;
        this.encodeur = encodeur;
        this.sessionService = sessionService;
        this.evenements = evenements;
        this.proprietes = proprietes;
        this.echecs = echecs;
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
     * Ordre identique que le compte existe ou non : lecture du compte, lecture du verrouillage du couple
     * (e-mail, IP), BCrypt (réel ou leurre), puis, en cas d'échec, la même écriture. Les échecs sont
     * enregistrés malgré l'exception : la transaction est validée.
     */
    @Transactional(noRollbackFor = IdentifiantsInvalidesException.class)
    public Session connecter(ConnexionRequete requete, Contexte contexte) {
        Instant maintenant = horloge.instant();
        String motDePasse = requete.motDePasse();
        boolean longueurAcceptable = motDePasse.getBytes(StandardCharsets.UTF_8).length <= OCTETS_MAX_MOT_DE_PASSE;
        String empreinteEmail = GenerateurJetons.empreinte(Utilisateur.normaliserEmail(requete.email()));
        String ip = contexte.adresseIp();
        Optional<Utilisateur> trouve = utilisateurDepot.trouverParEmail(requete.email());
        boolean verrouille = echecs.verrouillageEnCours(empreinteEmail, ip, maintenant).isPresent();

        if (verrouille) {
            // Même le bon mot de passe est refusé, avec la réponse générique
            encodeur.matches("leurre", empreinteLeurre);
            throw new IdentifiantsInvalidesException("couple (e-mail, IP " + ip + ") verrouillé"
                    + trouve.map(u -> ", compte " + u.getId()).orElse(""));
        }
        boolean correct;
        if (trouve.isPresent() && longueurAcceptable) {
            correct = encodeur.matches(motDePasse, trouve.get().getMotDePasseHash());
        } else {
            // E-mail inconnu, ou plus de 72 octets (BCryptPasswordEncoder lèverait une exception) : leurre
            encodeur.matches("leurre", empreinteLeurre);
            correct = false;
        }
        if (!correct) {
            boolean vientDeVerrouiller = echecs.enregistrerEchec(empreinteEmail, ip, maintenant,
                    proprietes.echecsAvantVerrouillage(), proprietes.dureeVerrouillage());
            trouve.filter(u -> vientDeVerrouiller).ifPresent(u -> {
                log.warn("SÉCURITÉ : connexion au compte {} verrouillée depuis l'IP {} pour {} après {} échecs",
                        u.getId(), ip, proprietes.dureeVerrouillage(), proprietes.echecsAvantVerrouillage());
                evenements.publishEvent(new CompteVerrouille(u.getId()));
            });
            throw new IdentifiantsInvalidesException(trouve
                    .map(u -> "mot de passe incorrect pour le compte " + u.getId())
                    .orElse("e-mail inconnu"));
        }
        Utilisateur utilisateur = trouve.get();
        if (!utilisateur.isActif()) {
            throw new IdentifiantsInvalidesException("compte " + utilisateur.getId() + " désactivé");
        }
        echecs.effacer(empreinteEmail, ip);
        return sessionService.ouvrir(utilisateur, contexte);
    }
}
