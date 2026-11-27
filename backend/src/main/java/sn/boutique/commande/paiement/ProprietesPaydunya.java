package sn.boutique.commande.paiement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Clés et mode de PayDunya (préfixe {@code boutique.paiement.paydunya}, variables {@code PAYDUNYA_*}).
 * Sans les trois clés, le mobile money n'est pas proposé au client.
 *
 * @param cleMaitre   clé principale (en-tête {@code PAYDUNYA-MASTER-KEY}) ; son SHA-512 sert aussi de {@code hash} des
 *                    notifications
 * @param clePrivee   clé privée ({@code PAYDUNYA-PRIVATE-KEY})
 * @param jeton       jeton d'application ({@code PAYDUNYA-TOKEN})
 * @param mode        {@code test} (bac à sable) ou {@code production}
 * @param nomBoutique nom du vendeur affiché sur la page de paiement ({@code store.name}, obligatoire chez PayDunya)
 */
@Validated
@ConfigurationProperties(prefix = "boutique.paiement.paydunya")
public record ProprietesPaydunya(
        String cleMaitre,
        String clePrivee,
        String jeton,
        @Pattern(regexp = "test|production") String mode,
        @NotBlank String nomBoutique) {

    static final String URL_TEST = "https://app.paydunya.com/sandbox-api/v1/";
    static final String URL_PRODUCTION = "https://app.paydunya.com/api/v1/";

    public boolean configure() {
        return rempli(cleMaitre) && rempli(clePrivee) && rempli(jeton);
    }

    public String urlBase() {
        return "production".equals(mode) ? URL_PRODUCTION : URL_TEST;
    }

    private static boolean rempli(String valeur) {
        return valeur != null && !valeur.isBlank();
    }

    /** Jamais les clés dans un journal ou un message d'erreur. */
    @Override
    public String toString() {
        return "ProprietesPaydunya[mode=" + mode + ", configure=" + configure() + "]";
    }
}
