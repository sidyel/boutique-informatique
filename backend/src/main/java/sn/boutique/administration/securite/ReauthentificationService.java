package sn.boutique.administration.securite;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.authentification.config.ProprietesSecondFacteur;
import sn.boutique.authentification.service.CodeSecondFacteurInvalideException;
import sn.boutique.authentification.service.SecondFacteurService;
import sn.boutique.authentification.service.SessionService.Contexte;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Fenêtre de réauthentification : un code TOTP juste ouvre dix minutes pendant lesquelles les actions sensibles sont
 * permises, pour <strong>cette session seulement</strong> (famille du jeton de rafraîchissement, revendication {@code sid}).
 * Un code de secours ne convient pas : il sert à entrer quand le téléphone est perdu, pas à valider une action.
 */
@Service
@RequiredArgsConstructor
public class ReauthentificationService {

    private final JdbcClient jdbc;
    private final UtilisateurDepot utilisateurs;
    private final SecondFacteurService secondFacteur;
    private final ProprietesSecondFacteur proprietes;
    private final Clock horloge;

    /** @return l'instant où la fenêtre se ferme */
    @Transactional(noRollbackFor = CodeSecondFacteurInvalideException.class)
    public Instant ouvrir(Long utilisateurId, UUID famille, String code, Contexte contexte) {
        Utilisateur utilisateur = utilisateurs.findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", utilisateurId));
        if (!secondFacteur.verifierCodeTotp(utilisateur, code, contexte)) {
            throw new CodeSecondFacteurInvalideException("code de réauthentification faux (compte " + utilisateurId + ")");
        }
        Instant jusqua = horloge.instant().plus(proprietes.dureeReauthentification());
        jdbc.sql("""
                insert into reauthentification (famille, utilisateur_id, valide_jusqu) values (:famille, :utilisateur, :jusqua)
                on conflict (famille) do update set valide_jusqu = excluded.valide_jusqu""")
                .param("famille", famille).param("utilisateur", utilisateurId).param("jusqua", Timestamp.from(jusqua))
                .update();
        return jusqua;
    }

    public boolean estValide(UUID famille) {
        return famille != null && jdbc.sql("select count(*) from reauthentification where famille = :famille and valide_jusqu > :maintenant")
                .param("famille", famille).param("maintenant", Timestamp.from(horloge.instant()))
                .query(Long.class).single() > 0;
    }

    /** Ferme les fenêtres d'un compte (changement de rôle, désactivation, second facteur réinitialisé). */
    public void fermerPourUtilisateur(Long utilisateurId) {
        jdbc.sql("delete from reauthentification where utilisateur_id = :id").param("id", utilisateurId).update();
    }

    @Scheduled(cron = "0 15 4 * * *", zone = "Africa/Dakar")
    public int purger() {
        return jdbc.sql("delete from reauthentification where valide_jusqu < :limite")
                .param("limite", Timestamp.from(horloge.instant())).update();
    }
}
