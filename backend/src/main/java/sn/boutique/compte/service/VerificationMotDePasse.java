package sn.boutique.compte.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.noyau.limitation.LimiteurDebit;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;

/**
 * Exige le mot de passe actuel avant une action sensible (changer de mot de passe ou d'e-mail, supprimer le compte).
 * Une session volée ne suffit pas à prendre le compte : il faut aussi connaître le mot de passe. Les essais sont limités
 * par compte (sinon une session volée servirait à deviner le mot de passe), et l'échec n'est <strong>jamais</strong> un
 * 401 : le frontend y verrait une session expirée.
 */
@Component
@RequiredArgsConstructor
public class VerificationMotDePasse {

    public static final String CODE = "MOT_DE_PASSE_ACTUEL_INCORRECT";
    private static final int OCTETS_MAX = 72;
    private static final Regle ESSAIS = new Regle(10, Duration.ofMinutes(15));

    private final PasswordEncoder encodeur;
    private final LimiteurDebit limiteur;

    /** @throws RegleMetierException (422) si le mot de passe est faux ; TropDeRequetesException (429) après trop d'essais */
    public void exiger(Utilisateur utilisateur, String saisi) {
        limiteur.verifier("mot-de-passe-actuel", ESSAIS, String.valueOf(utilisateur.getId()));
        boolean correct = saisi != null && saisi.getBytes(StandardCharsets.UTF_8).length <= OCTETS_MAX
                && encodeur.matches(saisi, utilisateur.getMotDePasseHash());
        if (!correct) {
            throw new RegleMetierException(CODE, "Le mot de passe actuel est incorrect.");
        }
    }
}
