package sn.boutique.panier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodePromoRequete(@NotBlank @Size(max = 40) String code) {
}
