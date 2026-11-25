package sn.boutique.marketing.dto;

import java.time.Instant;

/**
 * Bannière à afficher.
 *
 * @param dateFin fin de la campagne (compte à rebours), ou {@code null}
 */
public record BanniereReponse(
        Long id,
        String titre,
        String sousTitre,
        String texteBouton,
        String lien,
        String imageOrdinateurUrl,
        String imageMobileUrl,
        String texteAlternatif,
        Instant dateFin) {
}
