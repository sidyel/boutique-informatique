package sn.boutique.catalogue.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Nouvel ordre d'affichage : tous les identifiants des médias du produit, chacun une seule fois. */
public record OrdreMediasRequete(
        @NotEmpty(message = "La liste des médias est obligatoire.")
        @Size(max = 100, message = "Trop de médias.")
        List<@NotNull(message = "Identifiant de média manquant.") Long> ids) {
}
