package sn.boutique.catalogue.service;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Sections de produits de l'accueil ({@code boutique.accueil}).
 *
 * @param produitsParSection    cartes par section
 * @param seuilMeilleuresVentes nombre de produits ayant au moins une vente à partir duquel la section
 *                              « Meilleures ventes » existe ; en dessous, « Sélection du moment » (produits mis
 *                              en avant) : aucun titre n'affirme quelque chose de faux
 * @param ancienneteNouveautes  un produit est une « nouveauté » pendant cette durée après sa création
 */
@Validated
@ConfigurationProperties(prefix = "boutique.accueil")
public record ProprietesAccueil(
        @Min(1) @Max(24) int produitsParSection,
        @Min(1) int seuilMeilleuresVentes,
        @NotNull Duration ancienneteNouveautes) {
}
