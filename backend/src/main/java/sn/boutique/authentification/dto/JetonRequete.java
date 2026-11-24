package sn.boutique.authentification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Jeton reçu dans le lien d'un e-mail (vérification de l'adresse). */
public record JetonRequete(
        @NotBlank(message = "Le jeton est obligatoire")
        @Size(max = 100, message = "Le jeton n'est pas valide")
        String jeton) {

    @Override
    public String toString() {
        return "JetonRequete[***]";
    }
}
