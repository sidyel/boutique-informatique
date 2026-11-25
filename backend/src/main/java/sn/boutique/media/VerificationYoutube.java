package sn.boutique.media;

/**
 * Vérifie qu'une vidéo YouTube peut être intégrée au site. Implémentation : {@link VerificationYoutubeOembed} ;
 * les tests des routes utilisent un faux.
 */
public interface VerificationYoutube {

    /**
     * @throws sn.boutique.noyau.erreur.RegleMetierException {@code YOUTUBE_NON_INTEGRABLE} (vidéo privée ou
     *         intégration désactivée par son auteur) ou {@code YOUTUBE_INTROUVABLE} (supprimée, inexistante)
     * @throws YoutubeIndisponibleException YouTube ne répond pas dans le délai ou répond en erreur
     */
    VideoYoutube verifier(String identifiant);

    record VideoYoutube(String identifiant, String titre, String auteur) {
    }
}
