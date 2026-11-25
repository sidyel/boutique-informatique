package sn.boutique.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.boutique.catalogue.validation.TexteAlternatif;

/** Vidéo envoyée directement au stockage avec une signature de ce produit, déclarée une fois l'envoi terminé. */
public record DeclarationVideoRequete(
        @NotBlank(message = "L'idPublic de la vidéo est obligatoire.") @Size(max = 255) String idPublic,
        @NotNull(message = "Le texte alternatif est obligatoire.") @TexteAlternatif String texteAlternatif,
        Long varianteId) {
}
