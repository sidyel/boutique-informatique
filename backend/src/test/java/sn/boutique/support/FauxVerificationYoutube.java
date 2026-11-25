package sn.boutique.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import sn.boutique.media.VerificationYoutube;

/**
 * Remplace l'appel oEmbed dans les tests des routes (l'implémentation réelle est testée contre un faux
 * serveur HTTP). Par défaut, toute vidéo est publique et intégrable.
 */
public class FauxVerificationYoutube implements VerificationYoutube {

    private final Map<String, Function<String, VideoYoutube>> reponses = new ConcurrentHashMap<>();

    @Override
    public VideoYoutube verifier(String identifiant) {
        return reponses.getOrDefault(identifiant, id -> new VideoYoutube(id, "Présentation", "Boutique")).apply(identifiant);
    }

    /** Pour cet identifiant, lève l'exception donnée (refus, YouTube indisponible…). */
    public void repondre(String identifiant, RuntimeException erreur) {
        reponses.put(identifiant, id -> {
            throw erreur;
        });
    }

    public void reinitialiser() {
        reponses.clear();
    }
}
