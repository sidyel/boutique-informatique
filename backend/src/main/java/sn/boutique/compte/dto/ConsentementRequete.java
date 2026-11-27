package sn.boutique.compte.dto;

import jakarta.validation.constraints.NotNull;

/** Accepter ou retirer le consentement marketing : un seul champ, sans mot de passe. */
public record ConsentementRequete(@NotNull(message = "Le choix est obligatoire") Boolean consentement) {
}
