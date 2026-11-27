package sn.boutique.compte.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Action sensible qui ne demande que le mot de passe actuel (téléchargement des données personnelles). */
public record MotDePasseRequete(
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(max = 256, message = "Le mot de passe ne doit pas dépasser 256 caractères")
        String motDePasse) {

    @Override
    public String toString() {
        return "MotDePasseRequete[***]";
    }
}
