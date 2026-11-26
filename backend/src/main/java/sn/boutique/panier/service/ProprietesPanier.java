package sn.boutique.panier.service;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;

/**
 * Réglages du panier (préfixe {@code boutique.panier}).
 *
 * @param conservationInvite un panier invité sans modification depuis cette durée est supprimé
 * @param codePromoIp        essais de codes promo par IP (anti-énumération, valeur large pour le CGNAT)
 */
@Validated
@ConfigurationProperties(prefix = "boutique.panier")
public record ProprietesPanier(
        @Valid @NotNull Cookie cookie,
        @NotNull Duration conservationInvite,
        @Valid @NotNull Regle codePromoIp) {

    /** Cookie HttpOnly, SameSite=Lax : envoyé au retour depuis la page de paiement ou une publicité. */
    public record Cookie(@NotBlank String nom, @NotBlank String chemin, boolean secure, @NotNull Duration duree) {
    }
}
