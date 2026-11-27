package sn.boutique.commande.paiement;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Délais et réessais des appels au prestataire de paiement (préfixe {@code boutique.paiement.http}).
 *
 * @param delaiConnexion  temps maximal pour établir la connexion
 * @param delaiLecture    temps maximal pour obtenir la réponse. La somme des deux doit rester inférieure à
 *                        {@code boutique.commande.reservation-paiement}, sinon une seconde facture serait possible
 * @param reessaisLecture nombre de réessais d'une <strong>lecture</strong> (vérification d'une facture) après un
 *                        échec réseau, un délai dépassé ou une erreur 5xx. Jamais appliqué à la création d'une
 *                        facture : un réessai pourrait en créer deux
 * @param pauseReessai    attente avant un réessai, multipliée par le numéro du réessai
 */
@Validated
@ConfigurationProperties(prefix = "boutique.paiement.http")
public record ProprietesClientPaiement(
        @NotNull Duration delaiConnexion,
        @NotNull Duration delaiLecture,
        @Min(0) @Max(5) int reessaisLecture,
        @NotNull Duration pauseReessai) {
}
