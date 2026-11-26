package sn.boutique.commande.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.noyau.limitation.LimiteurDebit;
import sn.boutique.noyau.telephone.TelephoneSenegal;

/**
 * Limites de débit de la commande : par IP (valeur large : de nombreux abonnés mobiles partagent une adresse
 * publique, CGNAT) et par numéro de téléphone (le numéro est haché : Redis n'en contient aucun en clair).
 * Réglables dans {@code boutique.commande}.
 */
@Component
@RequiredArgsConstructor
public class LimitesCommande {

    private final LimiteurDebit limiteur;
    private final ProprietesCommande proprietes;

    /** Les règles vont de la plus large à la plus fine : une requête refusée ne consomme pas les quotas suivants. */
    public void creation(String ip, String telephone) {
        limiteur.verifier("commande-ip", proprietes.limiteIp(), ip);
        String numero = TelephoneSenegal.normaliser(telephone).orElse(telephone == null ? "" : telephone.strip());
        limiteur.verifier("commande-telephone", proprietes.limiteTelephone(), GenerateurJetons.empreinte(numero));
    }

    public void suivi(String ip) {
        limiteur.verifier("suivi-ip", proprietes.limiteSuiviIp(), ip);
    }
}
