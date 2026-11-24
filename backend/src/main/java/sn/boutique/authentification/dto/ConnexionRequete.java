package sn.boutique.authentification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pas de règle de robustesse ici : elle s'applique à la création du mot de passe, pas à sa saisie. */
public record ConnexionRequete(
        @NotBlank(message = "L'e-mail est obligatoire")
        @Size(max = 254, message = "L'e-mail ne doit pas dépasser 254 caractères")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(max = 256, message = "Le mot de passe ne doit pas dépasser 256 caractères")
        String motDePasse) {

    @Override
    public String toString() {
        return "ConnexionRequete[email=%s]".formatted(email);
    }
}
