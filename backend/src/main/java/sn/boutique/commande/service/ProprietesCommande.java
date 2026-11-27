package sn.boutique.commande.service;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import sn.boutique.noyau.limitation.ProprietesLimitation.Regle;

/**
 * Réglages techniques de la commande (préfixe {@code boutique.commande}). Les réglages commerciaux (plafond du
 * paiement à la livraison, délais d'annulation…) sont dans {@code ParametresSite}.
 *
 * @param limiteIp           créations de commande par IP : valeur large, car de nombreux abonnés mobiles partagent
 *                           la même adresse publique (CGNAT)
 * @param limiteTelephone    créations de commande par numéro de téléphone (le même client ne spamme pas)
 * @param limiteSuiviIp      lectures de pages de suivi par IP
 * @param dureeCleIdempotence durée de vie d'une clé {@code Idempotency-Key}
 * @param reservationPaiement durée pendant laquelle une commande est « réservée » le temps de créer sa facture chez le
 *                           prestataire (doit dépasser les délais du client HTTP, sinon deux factures sont possibles)
 * @param pauseAttentePaiement attente entre deux vérifications d'une requête qui trouve la commande réservée
 * @param validiteFacture    durée pendant laquelle une facture du prestataire est réutilisée par « Reprendre le
 *                           paiement » ; à régler sur la validité réelle de la facture chez le prestataire
 *                           (au moins le délai de paiement de la commande, sinon deux factures payables coexistent)
 */
@Validated
@ConfigurationProperties(prefix = "boutique.commande")
public record ProprietesCommande(
        @Valid @NotNull Regle limiteIp,
        @Valid @NotNull Regle limiteTelephone,
        @Valid @NotNull Regle limiteSuiviIp,
        @NotNull Duration dureeCleIdempotence,
        @NotNull Duration validiteFacture,
        @NotNull Duration reservationPaiement,
        @NotNull Duration pauseAttentePaiement) {
}
