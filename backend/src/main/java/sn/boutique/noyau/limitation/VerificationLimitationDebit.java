package sn.boutique.noyau.limitation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import sn.boutique.noyau.config.ProprietesBoutique;

/**
 * La limitation du débit protège la connexion, l'inscription et les envois d'e-mails contre le bourrage d'identifiants
 * et le spam. L'interrupteur {@code boutique.limitation.actif} existe pour la CI de performance (Lighthouse ouvre
 * des dizaines de sessions avec le même compte) et pour le développement.
 * <p>
 * <strong>Le démarrage échoue</strong> quand la limitation est coupée sur un environnement de production : indexation
 * autorisée, ou profil {@code prod} sans le profil {@code demo} (la pile de démonstration et de CI n'est jamais une
 * production). Même règle que pour les mentions légales des factures ; voir
 * {@code infra/docker-compose.ci-sans-limitation-debit.yml}.
 */
@Slf4j
@Component
public class VerificationLimitationDebit {

    public VerificationLimitationDebit(ProprietesLimitation limitation, ProprietesBoutique boutique, Environment environnement) {
        if (limitation.actif()) {
            return;
        }
        if (miseEnProduction(boutique, environnement)) {
            throw new IllegalStateException("La limitation du débit est désactivée (boutique.limitation.actif=false) sur un "
                    + "environnement de production (indexation autorisée, ou profil prod sans demo). Réactivez-la : "
                    + "sans elle, la connexion et l'inscription sont exposées au bourrage d'identifiants.");
        }
        log.warn("""

                ==========================================================================================
                AVERTISSEMENT : la limitation du débit est DÉSACTIVÉE (boutique.limitation.actif=false).
                Acceptable pour la CI de performance et le développement uniquement ; le démarrage est
                refusé en production (profil prod sans demo, ou indexation autorisée).
                ==========================================================================================""");
    }

    static boolean miseEnProduction(ProprietesBoutique boutique, Environment environnement) {
        return boutique.indexationAutorisee()
                || (environnement.acceptsProfiles(Profiles.of("prod")) && !environnement.acceptsProfiles(Profiles.of("demo")));
    }
}
