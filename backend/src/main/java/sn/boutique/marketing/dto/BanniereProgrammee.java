package sn.boutique.marketing.dto;

import java.time.Instant;

/**
 * Bannière active et sa période de diffusion, telle qu'elle est mise en cache. Le filtre des dates est
 * appliqué à chaque lecture, après le cache : une bannière apparaît et disparaît à l'heure prévue, même
 * si la liste est en cache depuis plusieurs minutes.
 */
public record BanniereProgrammee(BanniereReponse banniere, Instant dateDebut, Instant dateFin) {

    public boolean estVisibleA(Instant instant) {
        return (dateDebut == null || !instant.isBefore(dateDebut)) && (dateFin == null || instant.isBefore(dateFin));
    }
}
