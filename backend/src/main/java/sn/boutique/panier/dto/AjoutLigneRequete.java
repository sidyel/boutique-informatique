package sn.boutique.panier.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Ajout au panier : jamais de prix, il est relu sur la variante. */
public record AjoutLigneRequete(@NotNull Long varianteId, @Min(1) @Max(99) int quantite) {
}
