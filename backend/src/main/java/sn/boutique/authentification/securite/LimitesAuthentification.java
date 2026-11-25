package sn.boutique.authentification.securite;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.limitation.Comptage;
import sn.boutique.noyau.limitation.LimiteurDebit;
import sn.boutique.noyau.limitation.ProprietesLimitation;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;

/**
 * Limites de débit des routes d'authentification (ADR 0003 §9 et §13, relecture de sécurité I1 et M2).
 * <p>
 * L'IP est celle fournie par la valve RemoteIp de Tomcat : l'en-tête X-Forwarded-For n'est pris en compte
 * que s'il vient de nginx (réseau Docker interne), il ne peut donc pas servir à changer d'IP à chaque essai.
 * L'e-mail est normalisé (casse, espaces) puis haché : Redis ne contient aucune adresse en clair.
 * <p>
 * Les quotas qui bloquent (429) portent sur l'IP ou le couple (e-mail, IP) : un attaquant n'épuise que les
 * siens, jamais ceux de la victime qui se connecte d'ailleurs. Le quota par e-mail seul ne bloque jamais la
 * connexion (alerte seulement) ; pour les e-mails envoyés, il fait omettre l'envoi, sans changer la réponse.
 * Les règles sont vérifiées de la plus large à la plus fine : une requête refusée ne consomme pas les
 * quotas suivants.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimitesAuthentification {

    /** Marqueur des journaux : nombreuses tentatives de connexion sur un même compte, depuis plusieurs IP. */
    public static final String ALERTE_CONNEXION_CIBLEE = "ALERTE_CONNEXION_CIBLEE";

    private final LimiteurDebit limiteur;
    private final ProprietesLimitation regles;

    public void connexion(String ip, String email) {
        limiteur.verifier("connexion-ip", regles.connexionIp(), ip);
        String empreinte = empreinte(email);
        limiteur.verifier("connexion-email-ip", regles.connexionEmailIp(), empreinte + ":" + ip);
        Comptage global = limiteur.compter("connexion-email-alarme", regles.connexionEmailAlarme(), empreinte);
        if (global.franchit(regles.connexionEmailAlarme())) {
            // Jamais de blocage : le titulaire légitime doit pouvoir se connecter depuis sa propre IP
            log.error("{} : plus de {} tentatives de connexion en {} sur l'e-mail d'empreinte {}…, depuis plusieurs "
                            + "IP (attaque ciblée probable, la connexion n'est pas bloquée pour autant)",
                    ALERTE_CONNEXION_CIBLEE, regles.connexionEmailAlarme().limite(),
                    regles.connexionEmailAlarme().fenetre(), empreinte.substring(0, 12));
        }
    }

    public void inscription(String ip) {
        limiteur.verifier("inscription-ip", regles.inscriptionIp(), ip);
    }

    /** @return false si l'e-mail a dépassé son quota : répondre 202 comme d'habitude, sans rien envoyer */
    public boolean motDePasseOublie(String ip, String email) {
        return autoriserEnvoi("mot-de-passe-oublie", regles.motDePasseOublieIp(), regles.motDePasseOublieEmailIp(),
                regles.motDePasseOublieEmail(), ip, email);
    }

    /** @return false si l'e-mail a dépassé son quota : répondre 202 comme d'habitude, sans rien envoyer */
    public boolean renvoiVerification(String ip, String email) {
        return autoriserEnvoi("renvoi-verification", regles.renvoiVerificationIp(),
                regles.renvoiVerificationEmailIp(), regles.renvoiVerificationEmail(), ip, email);
    }

    public void rafraichir(String ip) {
        limiteur.verifier("rafraichir-ip", regles.rafraichirIp(), ip);
    }

    private boolean autoriserEnvoi(String route, Regle parIp, Regle parEmailEtIp, Regle parEmail, String ip,
                                   String email) {
        limiteur.verifier(route + "-ip", parIp, ip);
        String empreinte = empreinte(email);
        limiteur.verifier(route + "-email-ip", parEmailEtIp, empreinte + ":" + ip);
        Comptage comptage = limiteur.compter(route + "-email", parEmail, empreinte);
        if (comptage.depasse(parEmail)) {
            log.warn("Quota « {}-email » atteint ({} demandes en {}) : réponse 202 sans envoi d'e-mail", route,
                    comptage.nombre(), parEmail.fenetre());
            return false;
        }
        return true;
    }

    private static String empreinte(String email) {
        return GenerateurJetons.empreinte(Utilisateur.normaliserEmail(email));
    }
}
