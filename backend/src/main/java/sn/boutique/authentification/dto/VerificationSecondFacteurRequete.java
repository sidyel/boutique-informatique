package sn.boutique.authentification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un code TOTP à 6 chiffres <em>ou</em> un code de secours : exactement l'un des deux. */
public record VerificationSecondFacteurRequete(@NotBlank @Size(max = 200) String jetonEtape,
                                               @Size(max = 20) String code,
                                               @Size(max = 20) String codeSecours) {
}
