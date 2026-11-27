package sn.boutique.compte.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangementEmailRequete(
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(max = 256, message = "Le mot de passe ne doit pas dépasser 256 caractères")
        String motDePasse,
        @NotBlank(message = "La nouvelle adresse e-mail est obligatoire")
        @Email(message = "L'adresse e-mail n'est pas valide")
        @Size(max = 254, message = "L'adresse e-mail ne doit pas dépasser 254 caractères")
        String nouvelEmail) {

    @Override
    public String toString() {
        return "ChangementEmailRequete[***]";
    }
}
