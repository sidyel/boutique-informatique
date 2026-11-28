package sn.boutique.authentification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import sn.boutique.noyau.config.ProprietesBoutique;
import sn.boutique.noyau.limitation.VerificationLimitationDebit;

/**
 * Le secret TOTP fixe du compte de démonstration ({@code DEMO_ADMIN_TOTP_SECRET}) rend le second facteur prévisible : il ne sert
 * qu'à la CI de performance et aux captures. <strong>Le démarrage échoue</strong> s'il est défini sur un environnement de
 * production (indexation autorisée, ou profil {@code prod} sans le profil {@code demo}) : même règle que la limitation du
 * débit (ADR 0009 §10).
 */
@Slf4j
@Component
public class VerificationSecretTotpDemo {

    public VerificationSecretTotpDemo(ProprietesSecondFacteur proprietes, ProprietesBoutique boutique, Environment environnement) {
        if (!proprietes.secretFixeDemoDefini()) {
            return;
        }
        if (VerificationLimitationDebit.miseEnProduction(boutique, environnement)) {
            throw new IllegalStateException("DEMO_ADMIN_TOTP_SECRET est défini sur un environnement de production (indexation "
                    + "autorisée, ou profil prod sans demo). Ce secret fixe rend la double authentification prévisible : "
                    + "retirez-le.");
        }
        log.warn("""

                ==========================================================================================
                AVERTISSEMENT : DEMO_ADMIN_TOTP_SECRET est défini. Le second facteur du compte de démonstration
                est PRÉVISIBLE. Acceptable pour la CI de performance et le développement uniquement ; le
                démarrage est refusé en production.
                ==========================================================================================""");
    }
}
