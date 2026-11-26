package sn.boutique.panier.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record QuantiteRequete(@Min(1) @Max(99) int quantite) {
}
