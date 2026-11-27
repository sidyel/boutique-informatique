package sn.boutique.compte.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sn.boutique.authentification.validation.MotDePasseRobuste;

public record ChangementMotDePasseRequete(
        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        @Size(max = 256, message = "Le mot de passe actuel ne doit pas dépasser 256 caractères")
        String motDePasseActuel,
        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @MotDePasseRobuste
        String nouveauMotDePasse) {

    @Override
    public String toString() {
        return "ChangementMotDePasseRequete[***]";
    }
}
