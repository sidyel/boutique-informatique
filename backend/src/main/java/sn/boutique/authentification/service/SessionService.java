package sn.boutique.authentification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.authentification.config.ProprietesAuthentification;
import sn.boutique.authentification.depot.JetonRafraichissementDepot;
import sn.boutique.authentification.modele.JetonRafraichissement;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.authentification.service.JetonAccesService.JetonAcces;
import sn.boutique.compte.modele.Utilisateur;

/**
 * Sessions : jetons de rafraîchissement opaques, avec rotation et détection de réutilisation (ADR 0003).
 * <ul>
 *   <li>CLIENT : 30 jours, glissante (chaque rafraîchissement repart pour 30 jours) ;</li>
 *   <li>rôles internes : 12 heures, absolue (le rafraîchissement ne dépasse jamais la fin de session).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final JetonRafraichissementDepot jetonDepot;
    private final JetonAccesService jetonAccesService;
    private final ProprietesAuthentification proprietes;
    private final Clock horloge;

    /** Ouvre une session (nouvelle famille de jetons) après une connexion réussie. */
    @Transactional
    public Session ouvrir(Utilisateur utilisateur, Contexte contexte) {
        Instant maintenant = horloge.instant();
        Instant expiration = maintenant.plus(proprietes.dureeSession(utilisateur.getRole()));
        return emettre(utilisateur, UUID.randomUUID(), expiration, contexte, maintenant);
    }

    /**
     * Rotation : l'ancien jeton est marqué utilisé et un nouveau est émis dans la même famille.
     * Un jeton déjà utilisé trahit un vol (l'attaquant ou la victime a déjà fait la rotation) :
     * toute la famille est révoquée. La transaction est validée malgré l'exception, pour que
     * la révocation soit bien enregistrée.
     */
    @Transactional(noRollbackFor = SessionInvalideException.class)
    public Session rafraichir(String jeton, Contexte contexte) {
        if (jeton == null || jeton.isBlank()) {
            throw new SessionInvalideException("jeton absent");
        }
        // Verrou de ligne : deux rafraîchissements simultanés du même jeton sont traités l'un après l'autre
        JetonRafraichissement actuel = jetonDepot.trouverPourRotation(GenerateurJetons.empreinte(jeton))
                .orElseThrow(() -> new SessionInvalideException("jeton inconnu"));
        Instant maintenant = horloge.instant();
        Utilisateur utilisateur = actuel.getUtilisateur();

        if (actuel.getDateUtilisation() != null && !actuel.isRevoque()) {
            int revoques = jetonDepot.revoquerFamille(actuel.getFamille());
            log.warn("SÉCURITÉ : réutilisation d'un jeton de rafraîchissement (utilisateur {}, famille {}, IP {}) : "
                    + "{} jeton(s) révoqué(s)", utilisateur.getId(), actuel.getFamille(), contexte.adresseIp(), revoques);
            throw new SessionInvalideException("jeton réutilisé");
        }
        if (!actuel.estUtilisable(maintenant)) {
            throw new SessionInvalideException("jeton expiré ou révoqué");
        }
        if (!utilisateur.isActif()) {
            jetonDepot.revoquerFamille(actuel.getFamille());
            throw new SessionInvalideException("compte désactivé");
        }

        actuel.marquerUtilise(maintenant);
        Duration duree = proprietes.dureeSession(utilisateur.getRole());
        Instant expiration = utilisateur.getRole().estInterne()
                ? min(actuel.getDateExpiration(), maintenant.plus(duree))
                : maintenant.plus(duree);
        return emettre(utilisateur, actuel.getFamille(), expiration, contexte, maintenant);
    }

    /** Déconnexion : révoque toute la famille du jeton présenté. Sans effet si le jeton est inconnu. */
    @Transactional
    public void fermer(String jeton) {
        if (jeton == null || jeton.isBlank()) {
            return;
        }
        jetonDepot.findByEmpreinte(GenerateurJetons.empreinte(jeton))
                .ifPresent(trouve -> jetonDepot.revoquerFamille(trouve.getFamille()));
    }

    private Session emettre(Utilisateur utilisateur, UUID famille, Instant expiration, Contexte contexte,
                            Instant maintenant) {
        String jeton = GenerateurJetons.generer();
        jetonDepot.save(new JetonRafraichissement(utilisateur, GenerateurJetons.empreinte(jeton), expiration,
                famille, contexte.adresseIp(), contexte.userAgent()));
        JetonAcces acces = jetonAccesService.emettre(utilisateur);
        return new Session(utilisateur, acces, jeton, Duration.between(maintenant, expiration));
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    /** Informations de la requête conservées avec le jeton (liste des sessions, enquête en cas d'incident). */
    public record Contexte(String adresseIp, String userAgent) {
    }

    /**
     * @param jetonRafraichissement valeur en clair, à placer dans le cookie ; jamais stockée
     * @param dureeRestante         durée de vie du jeton de rafraîchissement (Max-Age du cookie)
     */
    public record Session(Utilisateur utilisateur, JetonAcces jetonAcces, String jetonRafraichissement,
                          Duration dureeRestante) {
    }
}
