package sn.boutique.compte.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sn.boutique.noyau.telephone.TelephoneSenegalais;

/** Nom et téléphone du profil. Téléphone facultatif (vide ou absent : effacé), stocké au format E.164. */
public record ModificationProfilRequete(
        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 80, message = "Le prénom ne doit pas dépasser 80 caractères")
        String prenom,
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 80, message = "Le nom ne doit pas dépasser 80 caractères")
        String nom,
        @TelephoneSenegalais
        String telephone) {
}
