package sn.boutique.compte.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sn.boutique.noyau.telephone.TelephoneSenegalais;

/** Adresse du carnet : le quartier et le point de repère comptent plus que le nom de rue. */
public record AdresseRequete(
        @NotBlank(message = "Le nom de l'adresse est obligatoire (ex. Maison, Bureau)")
        @Size(max = 40, message = "Le nom de l'adresse ne doit pas dépasser 40 caractères")
        String libelle,
        @NotBlank(message = "Le nom du destinataire est obligatoire")
        @Size(max = 120, message = "Le nom ne doit pas dépasser 120 caractères")
        String nomComplet,
        @NotBlank(message = "Le téléphone est obligatoire")
        @TelephoneSenegalais
        String telephone,
        @NotBlank(message = "La ville est obligatoire")
        @Size(max = 80, message = "La ville ne doit pas dépasser 80 caractères")
        String ville,
        @NotBlank(message = "Le quartier est obligatoire")
        @Size(max = 80, message = "Le quartier ne doit pas dépasser 80 caractères")
        String quartier,
        @Size(max = 255, message = "L'adresse ne doit pas dépasser 255 caractères")
        String adresseLigne,
        @Size(max = 255, message = "Le point de repère ne doit pas dépasser 255 caractères")
        String pointDeRepere,
        Boolean parDefaut) {
}
