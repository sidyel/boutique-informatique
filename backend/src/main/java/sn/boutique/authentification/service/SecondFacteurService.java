package sn.boutique.authentification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.administration.journal.JournalAdministration;
import sn.boutique.administration.journal.JournalAdministration.Acteur;
import sn.boutique.authentification.config.ProprietesSecondFacteur;
import sn.boutique.authentification.depot.CodeSecoursDepot;
import sn.boutique.authentification.depot.EtapeConnexionDepot;
import sn.boutique.authentification.modele.CodeSecours;
import sn.boutique.authentification.modele.EtapeConnexion;
import sn.boutique.authentification.modele.EtapeConnexion.Etape;
import sn.boutique.authentification.securite.Base32;
import sn.boutique.authentification.securite.ChiffrementSecrets;
import sn.boutique.authentification.securite.CodeQr;
import sn.boutique.authentification.securite.GenerateurCodesSecours;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.authentification.securite.LimitesSecondFacteur;
import sn.boutique.authentification.securite.Totp;
import sn.boutique.authentification.service.SessionService.Contexte;
import sn.boutique.authentification.service.SessionService.Session;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Utilisateur;

/**
 * Connexion en deux temps des comptes internes (ADR 0009) : jeton d'étape, enrôlement du TOTP, codes de secours.
 * <p>
 * Un code faux compte un essai sur le jeton d'étape <strong>malgré l'exception</strong> (la transaction est validée) ;
 * au-delà de {@code essais-par-etape}, il faut refaire le mot de passe. Le jeton d'étape est verrouillé en base le temps du
 * traitement : deux requêtes simultanées avec le même jeton ne passent pas ensemble.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecondFacteurService {

    private static final String MARQUE = "SÉCURITÉ";

    private final EtapeConnexionDepot etapes;
    private final CodeSecoursDepot codesSecours;
    private final UtilisateurDepot utilisateurs;
    private final ChiffrementSecrets chiffrement;
    private final SessionService sessions;
    private final LimitesSecondFacteur limites;
    private final ProprietesSecondFacteur proprietes;
    private final JournalAdministration journal;
    private final Clock horloge;

    /** Étape suivante de la connexion : jamais une session. */
    public record EtapeSuivante(Etape etape, String jetonEtape) {
    }

    /** Ce que l'écran d'enrôlement affiche : secret (saisie manuelle), adresse otpauth et QR code en SVG ({@code data:}). */
    public record Enrolement(String secret, String uri, String codeQr) {
    }

    /**
     * Résultat d'une étape réussie : soit une session (connexion terminée), soit l'étape suivante, avec les codes de
     * secours en clair quand il faut les présenter (une seule fois).
     */
    public record Resultat(Session session, Etape etape, String jetonEtape, List<String> codesSecours) {

        static Resultat terminee(Session session) {
            return new Resultat(session, null, null, List.of());
        }

        static Resultat codes(String jeton, List<String> codes) {
            return new Resultat(null, Etape.CODES_SECOURS_A_CONFIRMER, jeton, codes);
        }
    }

    /** Appelé quand le mot de passe d'un compte interne est correct : ouvre l'étape, sans aucun droit d'accès. */
    @Transactional
    public EtapeSuivante ouvrirEtape(Utilisateur utilisateur, Contexte contexte) {
        Instant maintenant = horloge.instant();
        // Un compte qui se reconnecte abandonne ses étapes précédentes
        etapes.invaliderPourUtilisateur(utilisateur.getId(), maintenant);
        Etape etape = utilisateur.secondFacteurActif() ? Etape.CODE_REQUIS : Etape.ENROLEMENT_REQUIS;
        String jeton = GenerateurJetons.generer();
        etapes.save(new EtapeConnexion(utilisateur, GenerateurJetons.empreinte(jeton), etape, contexte.adresseIp(),
                contexte.userAgent(), maintenant.plus(proprietes.dureeEtape()),
                maintenant.plus(proprietes.dureeEtapeMaximale())));
        return new EtapeSuivante(etape, jeton);
    }

    /** Génère un secret, le garde chiffré avec l'étape, et le présente. Un nouvel appel le remplace. */
    @Transactional
    public Enrolement demarrerEnrolement(String jeton) {
        EtapeConnexion etape = charger(jeton, Etape.ENROLEMENT_REQUIS);
        Utilisateur utilisateur = etape.getUtilisateur();
        String secret = Totp.genererSecret();
        etape.memoriserSecretEnAttente(chiffrement.chiffrer(secret, utilisateur.getId()));
        etape.passerA(Etape.ENROLEMENT_REQUIS, horloge.instant(), proprietes.dureeEtape());
        String uri = Totp.uri(proprietes.emetteur(), utilisateur.getEmail(), secret);
        return new Enrolement(secret, uri, CodeQr.versSvg(uri));
    }

    /** Premier code valide : le second facteur est activé et les codes de secours sont présentés (à confirmer). */
    @Transactional(noRollbackFor = {CodeSecondFacteurInvalideException.class, EtapeInvalideException.class})
    public Resultat confirmerEnrolement(String jeton, String code, Contexte contexte) {
        EtapeConnexion etape = charger(jeton, Etape.ENROLEMENT_REQUIS);
        Utilisateur utilisateur = etape.getUtilisateur();
        if (etape.getSecretEnAttenteChiffre() == null) {
            throw new EtapeInvalideException("enrôlement confirmé sans secret généré (compte " + utilisateur.getId() + ")");
        }
        Instant maintenant = horloge.instant();
        limites.code(utilisateur.getId(), contexte.adresseIp());
        String secret = chiffrement.dechiffrer(etape.getSecretEnAttenteChiffre(), utilisateur.getId());
        if (!totpAccepte(utilisateur, secret, code, maintenant)) {
            refuser(etape, utilisateur, contexte, "code d'enrôlement faux");
        }
        utilisateur.activerSecondFacteur(chiffrement.chiffrer(secret, utilisateur.getId()), maintenant);
        List<String> nouveaux = regenererCodes(utilisateur);
        etape.memoriserSecretEnAttente(null);
        etape.passerA(Etape.CODES_SECOURS_A_CONFIRMER, maintenant, proprietes.dureeEtape());
        log.info("{} : second facteur activé pour le compte {}", MARQUE, utilisateur.getId());
        tracer(utilisateur, contexte, "SECOND_FACTEUR_ACTIVE", null);
        return Resultat.codes(jeton, nouveaux);
    }

    /** Code TOTP (6 chiffres) ou code de secours : l'un des deux, pas les deux. */
    @Transactional(noRollbackFor = {CodeSecondFacteurInvalideException.class, EtapeInvalideException.class})
    public Resultat verifier(String jeton, String code, String codeSecours, Contexte contexte) {
        EtapeConnexion etape = charger(jeton, Etape.CODE_REQUIS);
        Utilisateur utilisateur = etape.getUtilisateur();
        boolean parTotp = code != null && !code.isBlank();
        boolean parSecours = codeSecours != null && !codeSecours.isBlank();
        if (parTotp == parSecours) {
            throw new CodeSecondFacteurInvalideException("il faut un code TOTP ou un code de secours, un seul");
        }
        Instant maintenant = horloge.instant();
        limites.code(utilisateur.getId(), contexte.adresseIp());
        boolean accepte;
        if (parTotp) {
            String secret = chiffrement.dechiffrer(utilisateur.getTotpSecretChiffre(), utilisateur.getId());
            accepte = totpAccepte(utilisateur, secret, code, maintenant);
        } else {
            accepte = GenerateurCodesSecours.bienForme(codeSecours)
                    && codesSecours.consommer(utilisateur.getId(), GenerateurCodesSecours.empreinte(codeSecours),
                    maintenant) == 1;
            if (accepte) {
                tracer(utilisateur, contexte, "CODE_SECOURS_UTILISE", "restants : "
                        + codesSecours.compterRestants(utilisateur.getId()));
                log.warn("{} : code de secours utilisé par le compte {} ({} restant(s))", MARQUE, utilisateur.getId(),
                        codesSecours.compterRestants(utilisateur.getId()));
            }
        }
        if (!accepte) {
            refuser(etape, utilisateur, contexte, parTotp ? "code TOTP faux" : "code de secours faux");
        }
        if (utilisateur.getCodesSecoursConfirmesLe() == null) {
            // Les codes n'ont jamais été confirmés : en présenter de nouveaux avant d'ouvrir la session
            List<String> nouveaux = regenererCodes(utilisateur);
            etape.passerA(Etape.CODES_SECOURS_A_CONFIRMER, maintenant, proprietes.dureeEtape());
            return Resultat.codes(jeton, nouveaux);
        }
        etape.marquerUtilisee(maintenant);
        tracer(utilisateur, contexte, "CONNEXION_EQUIPE", parTotp ? "code TOTP" : "code de secours");
        return Resultat.terminee(sessions.ouvrir(utilisateur, contexte, true));
    }

    /** « Je les ai imprimés ou enregistrés » : la session s'ouvre. */
    @Transactional(noRollbackFor = EtapeInvalideException.class)
    public Resultat confirmerCodesSecours(String jeton, Contexte contexte) {
        EtapeConnexion etape = charger(jeton, Etape.CODES_SECOURS_A_CONFIRMER);
        Utilisateur utilisateur = etape.getUtilisateur();
        Instant maintenant = horloge.instant();
        utilisateur.confirmerCodesSecours(maintenant);
        etape.marquerUtilisee(maintenant);
        log.info("{} : codes de secours confirmés par le compte {}", MARQUE, utilisateur.getId());
        tracer(utilisateur, contexte, "CONNEXION_EQUIPE", "après confirmation des codes de secours");
        return Resultat.terminee(sessions.ouvrir(utilisateur, contexte, true));
    }

    // ------------------------------------------------------------------------------------------------

    /** Remplace les codes de secours du compte par dix nouveaux, non confirmés ; renvoie leur valeur en clair. */
    @Transactional
    public List<String> regenererCodes(Utilisateur utilisateur) {
        codesSecours.supprimerPourUtilisateur(utilisateur.getId());
        List<String> clairs = GenerateurCodesSecours.generer();
        List<CodeSecours> lignes = new ArrayList<>(clairs.size());
        for (String clair : clairs) {
            lignes.add(new CodeSecours(utilisateur, GenerateurCodesSecours.empreinte(clair)));
        }
        codesSecours.saveAll(lignes);
        utilisateur.invalideCodesSecours();
        return clairs;
    }

    /**
     * Vérifie un code TOTP contre un secret et mémorise le pas, atomiquement : un code accepté une fois ne l'est plus,
     * même en deux requêtes simultanées.
     */
    boolean totpAccepte(Utilisateur utilisateur, String secretBase32, String saisi, Instant maintenant) {
        OptionalLong pas = Totp.verifier(Base32.decoder(secretBase32), saisi, maintenant);
        return pas.isPresent() && utilisateurs.avancerPasTotp(utilisateur.getId(), pas.getAsLong()) == 1;
    }

    /** Vérifie un code TOTP du compte (réauthentification) : même anti-rejeu que la connexion. */
    @Transactional
    public boolean verifierCodeTotp(Utilisateur utilisateur, String saisi, Contexte contexte) {
        if (!utilisateur.secondFacteurActif()) {
            return false;
        }
        limites.code(utilisateur.getId(), contexte.adresseIp());
        String secret = chiffrement.dechiffrer(utilisateur.getTotpSecretChiffre(), utilisateur.getId());
        return totpAccepte(utilisateur, secret, saisi, horloge.instant());
    }

    /** Événement de sécurité d'un compte qui n'a pas (encore) de session : jamais le code saisi. */
    private void tracer(Utilisateur utilisateur, Contexte contexte, String action, String details) {
        journal.enregistrerPour(new Acteur(utilisateur.getId(), utilisateur.getEmail(), utilisateur.getRole().name()),
                action, "Utilisateur", utilisateur.getId(), details);
    }

    private EtapeConnexion charger(String jeton, Etape attendue) {
        if (jeton == null || jeton.isBlank()) {
            throw new EtapeInvalideException("jeton d'étape absent");
        }
        EtapeConnexion etape = etapes.trouverPourMiseAJour(GenerateurJetons.empreinte(jeton))
                .orElseThrow(() -> new EtapeInvalideException("jeton d'étape inconnu"));
        Utilisateur utilisateur = etape.getUtilisateur();
        if (!etape.estUtilisable(horloge.instant(), proprietes.essaisParEtape())) {
            throw new EtapeInvalideException("jeton d'étape expiré, utilisé ou épuisé (compte " + utilisateur.getId() + ")");
        }
        if (!utilisateur.isActif() || !utilisateur.getRole().estInterne()) {
            throw new EtapeInvalideException("compte " + utilisateur.getId() + " désactivé ou plus interne");
        }
        if (etape.getEtape() != attendue) {
            throw new EtapeInvalideException("étape " + etape.getEtape() + " présentée à l'étape " + attendue);
        }
        return etape;
    }

    private void refuser(EtapeConnexion etape, Utilisateur utilisateur, Contexte contexte, String raison) {
        etape.compterEchec();
        tracer(utilisateur, contexte, "CODE_SECOND_FACTEUR_REFUSE", raison + " (essai " + etape.getEssais() + ")");
        log.warn("{} : {} (compte {}, IP {}, essai {}/{})", MARQUE, raison, utilisateur.getId(), contexte.adresseIp(),
                etape.getEssais(), proprietes.essaisParEtape());
        throw new CodeSecondFacteurInvalideException(raison);
    }
}
