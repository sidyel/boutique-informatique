package sn.boutique.authentification.service;

import java.time.Clock;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.authentification.config.ProprietesAuthentification;
import sn.boutique.authentification.depot.JetonRafraichissementDepot;
import sn.boutique.authentification.depot.JetonReinitialisationMotDePasseDepot;
import sn.boutique.authentification.depot.JetonVerificationEmailDepot;

/**
 * Purge quotidienne des jetons expirés (rafraîchissement, vérification d'e-mail, réinitialisation).
 * <p>
 * Un jeton n'est supprimé qu'après un délai de conservation suivant son expiration
 * ({@code conservation-jetons-expires}, 7 jours par défaut) : les jetons d'une session volée restent
 * consultables quelques jours pour une enquête (IP, user agent). Un jeton encore valide, même révoqué
 * ou utilisé, n'est jamais supprimé : la détection de réutilisation en a besoin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgeJetonsExpires {

    private final JetonRafraichissementDepot rafraichissementDepot;
    private final JetonVerificationEmailDepot verificationDepot;
    private final JetonReinitialisationMotDePasseDepot reinitialisationDepot;
    private final ProprietesAuthentification proprietes;
    private final Clock horloge;

    /** Chaque nuit à 3 h 30, heure de Dakar (faible trafic). */
    @Scheduled(cron = "${boutique.authentification.purge-cron:0 30 3 * * *}", zone = "Africa/Dakar")
    @Transactional
    public Bilan purger() {
        Instant limite = horloge.instant().minus(proprietes.conservationJetonsExpires());
        Bilan bilan = new Bilan(
                rafraichissementDepot.supprimerExpiresAvant(limite),
                verificationDepot.supprimerExpiresAvant(limite),
                reinitialisationDepot.supprimerExpiresAvant(limite));
        log.info("Purge des jetons expirés avant {} : {} de rafraîchissement, {} de vérification, {} de réinitialisation",
                limite, bilan.rafraichissement(), bilan.verification(), bilan.reinitialisation());
        return bilan;
    }

    public record Bilan(int rafraichissement, int verification, int reinitialisation) {
    }
}
