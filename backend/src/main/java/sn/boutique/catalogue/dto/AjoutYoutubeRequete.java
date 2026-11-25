package sn.boutique.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.boutique.catalogue.validation.TexteAlternatif;

/** Vidéo YouTube : lien collé par l'administrateur (watch, youtu.be, shorts, embed). */
public record AjoutYoutubeRequete(
        @NotBlank(message = "Le lien YouTube est obligatoire.") @Size(max = 500) String lien,
        @NotNull(message = "Le texte alternatif est obligatoire.") @TexteAlternatif String texteAlternatif,
        Long varianteId) {
}
