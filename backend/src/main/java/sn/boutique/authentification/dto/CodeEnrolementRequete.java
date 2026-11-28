package sn.boutique.authentification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodeEnrolementRequete(@NotBlank @Size(max = 200) String jetonEtape, @NotBlank @Size(max = 20) String code) {
}
