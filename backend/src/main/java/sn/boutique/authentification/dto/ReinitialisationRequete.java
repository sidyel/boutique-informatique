package sn.boutique.authentification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sn.boutique.authentification.validation.MotDePasseRobuste;

public record ReinitialisationRequete(
        @NotBlank(message = "Le jeton est obligatoire")
        @Size(max = 100, message = "Le jeton n'est pas valide")
        String jeton,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @MotDePasseRobuste
        String motDePasse) {

    @Override
    public String toString() {
        return "ReinitialisationRequete[***]";
    }
}
