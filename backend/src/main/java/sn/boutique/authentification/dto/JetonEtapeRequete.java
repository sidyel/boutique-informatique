package sn.boutique.authentification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JetonEtapeRequete(@NotBlank @Size(max = 200) String jetonEtape) {
}
