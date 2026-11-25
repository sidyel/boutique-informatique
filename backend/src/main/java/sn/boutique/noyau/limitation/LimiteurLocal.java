package sn.boutique.noyau.limitation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;

/**
 * Limiteur de <strong>secours</strong>, en mémoire de l'instance, utilisé uniquement quand Redis est en erreur
 * (arrêté, injoignable ou plein) : les routes d'authentification restent limitées pendant la panne, au lieu
 * de tout laisser passer (relecture de sécurité, I2 ; ADR 0003 §9).
 * <p>
 * Même fenêtre fixe que dans Redis. <strong>Borné</strong> : au plus {@value #ENTREES_MAX} compteurs ; au-delà,
 * le moins récemment utilisé est oublié (quelques Mo au plus, même sous une avalanche d'e-mails inventés).
 * Un attaquant qui touche ses propres compteurs à chaque requête les garde parmi les plus récents.
 * <p>
 * Propre à chaque instance du backend : avec N instances, la limite effective est au plus N fois la règle.
 * Aucune dépendance ajoutée (LinkedHashMap en ordre d'accès, synchronisé : appelé seulement pendant une panne).
 */
@Component
public class LimiteurLocal {

    static final int ENTREES_MAX = 10_000;

    private final Clock horloge;
    private final Map<String, Compteur> compteurs;

    @Autowired
    public LimiteurLocal(Clock horloge) {
        this(horloge, ENTREES_MAX);
    }

    LimiteurLocal(Clock horloge, int entreesMax) {
        this.horloge = horloge;
        this.compteurs = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Compteur> plusAncienne) {
                return size() > entreesMax;
            }
        };
    }

    /** Compte une requête ; lève {@link TropDeRequetesException} au-delà de la limite de la fenêtre en cours. */
    public synchronized void verifier(String nom, Regle regle, String cle) {
        Instant maintenant = horloge.instant();
        String identifiant = nom + ":" + cle;
        Compteur compteur = compteurs.get(identifiant);
        if (compteur == null || !maintenant.isBefore(compteur.fin)) {
            compteur = new Compteur(maintenant.plus(regle.fenetre()));
            compteurs.put(identifiant, compteur);
        }
        compteur.nombre++;
        if (compteur.nombre > regle.limite()) {
            long millis = Duration.between(maintenant, compteur.fin).toMillis();
            throw new TropDeRequetesException(nom, Math.max(1, (millis + 999) / 1000));
        }
    }

    synchronized int taille() {
        return compteurs.size();
    }

    private static final class Compteur {
        private final Instant fin;
        private int nombre;

        private Compteur(Instant fin) {
            this.fin = fin;
        }
    }
}
