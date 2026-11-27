package sn.boutique.compte.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Suppression du compte : mot de passe actuel et saisie du mot de confirmation (confirmation renforcée). */
public record SuppressionCompteRequete(
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(max = 256, message = "Le mot de passe ne doit pas dépasser 256 caractères")
        String motDePasse,
        @NotBlank(message = "Saisissez SUPPRIMER pour confirmer")
        @Size(max = 40, message = "La confirmation est trop longue")
        String confirmation) {

    @Override
    public String toString() {
        return "SuppressionCompteRequete[***]";
    }
}
