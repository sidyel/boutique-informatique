package sn.boutique.noyau.consentement;

import org.springframework.stereotype.Service;

/**
 * Consentement du visiteur aux traceurs publicitaires (Google Consent Mode v2, pixel Meta).
 * <p>
 * Coquille de la phase 4 : <strong>refuse toujours</strong>. Tant que le bandeau de consentement n'existe pas
 * (phase 7), aucun identifiant publicitaire (gclid, fbclid, _fbp, _fbc) n'est enregistré avec une commande.
 * Les UTM, la page d'arrivée et le référent ne sont pas concernés (aucun identifiant individuel).
 */
@Service
public class ConsentementService {

    public enum Finalite {
        /** Identifiants de clic et cookies Meta, conversions côté serveur. */
        MESURE_PUBLICITAIRE
    }

    /** @return vrai si le visiteur a consenti à cette finalité ; toujours faux avant la phase 7 */
    public boolean autorise(Finalite finalite) {
        return false;
    }
}
