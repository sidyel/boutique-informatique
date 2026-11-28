package sn.boutique.authentification.securite;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import sn.boutique.authentification.config.ProprietesSecondFacteur;
import sn.boutique.noyau.limitation.LimiteurDebit;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;

/**
 * Limites de saisie des codes du second facteur (ADR 0009 §2) : par IP (large, plusieurs abonnés mobiles partagent
 * une IP) puis par compte. Chaque code saisi compte, juste ou faux : un dépassement répond 429 sans consommer le code.
 */
@Component
@RequiredArgsConstructor
public class LimitesSecondFacteur {

    private final LimiteurDebit limiteur;
    private final ProprietesSecondFacteur proprietes;

    public void code(long utilisateurId, String adresseIp) {
        limiteur.verifier("second-facteur-ip", new Regle(proprietes.tentativesParIp(), proprietes.fenetreIp()), adresseIp);
        limiteur.verifier("second-facteur-compte",
                new Regle(proprietes.tentativesParCompte(), proprietes.fenetreCompte()), String.valueOf(utilisateurId));
    }
}
