package sn.boutique.commande.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.boutique.commande.modele.ModeLivraison;
import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.noyau.telephone.TelephoneSenegalais;

/**
 * Création d'une commande à partir du panier. Aucun prix : montants et lignes viennent du panier du serveur.
 * {@code totalAttendu} est le total que le client a sous les yeux : s'il a changé (prix, frais de livraison,
 * code promo), la commande est refusée (409) et le client revoit le nouveau total avant de payer.
 *
 * @param email          facultatif pour un invité (facture et suivi par e-mail s'il existe)
 * @param zoneId         obligatoire pour la livraison à domicile
 * @param totalAttendu   FCFA
 * @param modePaiement   MOBILE_MONEY, CARTE_BANCAIRE ou A_LA_LIVRAISON
 * @param attribution    origine marketing capturée à l'arrivée sur le site (facultative)
 */
public record CreationCommandeRequete(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 120, message = "Le nom ne doit pas dépasser 120 caractères")
        String nomComplet,

        @NotBlank(message = "Le téléphone est obligatoire")
        @TelephoneSenegalais
        String telephone,

        @Email(message = "L'e-mail n'est pas valide")
        @Size(max = 254, message = "L'e-mail ne doit pas dépasser 254 caractères")
        String email,

        @NotNull(message = "Le mode de livraison est obligatoire")
        ModeLivraison modeLivraison,

        Long zoneId,

        @Size(max = 80, message = "La ville ne doit pas dépasser 80 caractères")
        String ville,

        @Size(max = 80, message = "Le quartier ne doit pas dépasser 80 caractères")
        String quartier,

        @Size(max = 255, message = "L'adresse ne doit pas dépasser 255 caractères")
        String adresse,

        @Size(max = 255, message = "Le point de repère ne doit pas dépasser 255 caractères")
        String pointDeRepere,

        /** Autre numéro de la personne qui réceptionne ; par défaut, le téléphone de la commande. */
        @TelephoneSenegalais
        String telephoneLivraison,

        @NotNull(message = "Le mode de paiement est obligatoire")
        ModePaiement modePaiement,

        @NotNull(message = "Le total attendu est obligatoire")
        Long totalAttendu,

        @Valid
        AttributionRequete attribution) {

    /** Origine marketing ; toutes les valeurs sont nettoyées et tronquées côté serveur. */
    public record AttributionRequete(
            @Size(max = 200) String utmSource,
            @Size(max = 200) String utmMedium,
            @Size(max = 300) String utmCampagne,
            @Size(max = 300) String utmContenu,
            @Size(max = 300) String utmTerme,
            @Size(max = 1000) String pageArrivee,
            @Size(max = 1000) String referent,
            @Size(max = 200) String premiereSource,
            @Size(max = 200) String premierMedium,
            @Size(max = 300) String premiereCampagne,
            @Size(max = 1000) String premierePageArrivee,
            /** Identifiants de clic et cookies Meta : ignorés sans consentement (phase 7). */
            @Size(max = 500) String gclid,
            @Size(max = 500) String fbclid,
            @Size(max = 200) String fbp,
            @Size(max = 500) String fbc) {
    }

    /** Empreinte du contenu métier : la même clé d'idempotence avec une autre demande est refusée. */
    public String empreinte() {
        return sn.boutique.authentification.securite.GenerateurJetons.empreinte(String.join("\u001f",
                nullable(nomComplet), nullable(telephone), nullable(email), String.valueOf(modeLivraison),
                String.valueOf(zoneId), nullable(ville), nullable(quartier), nullable(adresse),
                nullable(pointDeRepere), nullable(telephoneLivraison), String.valueOf(modePaiement),
                String.valueOf(totalAttendu)));
    }

    private static String nullable(String valeur) {
        return valeur == null ? "" : valeur.strip();
    }
}
