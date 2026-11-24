package sn.boutique.authentification.securite;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.limitation.LimiteurDebit;
import sn.boutique.noyau.limitation.ProprietesLimitation;

/**
 * Limites de débit des routes d'authentification (ADR 0003 §9 et §13).
 * <p>
 * L'IP est celle fournie par la valve RemoteIp de Tomcat : l'en-tête X-Forwarded-For n'est pris en compte
 * que s'il vient de nginx (réseau Docker interne), il ne peut donc pas servir à changer d'IP à chaque essai.
 * L'e-mail est normalisé (casse, espaces) puis haché : Redis ne contient aucune adresse en clair.
 * La limite par IP est vérifiée en premier : une requête bloquée par IP ne consomme pas le quota de l'e-mail.
 */
@Component
@RequiredArgsConstructor
public class LimitesAuthentification {

    private final LimiteurDebit limiteur;
    private final ProprietesLimitation regles;

    public void connexion(String ip, String email) {
        limiteur.verifier("connexion-ip", regles.connexionIp(), ip);
        limiteur.verifier("connexion-email", regles.connexionEmail(), empreinte(email));
    }

    public void inscription(String ip) {
        limiteur.verifier("inscription-ip", regles.inscriptionIp(), ip);
    }

    public void motDePasseOublie(String ip, String email) {
        limiteur.verifier("mot-de-passe-oublie-ip", regles.motDePasseOublieIp(), ip);
        limiteur.verifier("mot-de-passe-oublie-email", regles.motDePasseOublieEmail(), empreinte(email));
    }

    public void renvoiVerification(String ip, String email) {
        limiteur.verifier("renvoi-verification-ip", regles.renvoiVerificationIp(), ip);
        limiteur.verifier("renvoi-verification-email", regles.renvoiVerificationEmail(), empreinte(email));
    }

    public void rafraichir(String ip) {
        limiteur.verifier("rafraichir-ip", regles.rafraichirIp(), ip);
    }

    private static String empreinte(String email) {
        return GenerateurJetons.empreinte(Utilisateur.normaliserEmail(email));
    }
}
