package sn.boutique.authentification.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Second facteur des comptes internes (préfixe {@code boutique.second-facteur}), voir ADR 0009.
 *
 * @param cleChiffrement            clé AES des secrets TOTP, en Base64, 32 octets minimum une fois décodée (variable
 *                                  ADMIN_CLE_CHIFFREMENT, jamais en base ni dans le code) ; le démarrage est refusé sans elle
 * @param cleChiffrementPrecedente  ancienne clé, pendant une rotation seulement (ADMIN_CLE_CHIFFREMENT_PRECEDENTE)
 * @param emetteur                  nom affiché dans l'application d'authentification
 * @param secretFixeDemo            secret TOTP fixe du compte de démonstration (Base32) : CI et captures seulement ;
 *                                  le démarrage est refusé en production s'il est défini
 * @param dureeEtape                validité du jeton d'étape, renouvelée à chaque étape réussie
 * @param dureeEtapeMaximale        durée totale maximale d'une connexion en deux temps
 * @param essaisParEtape            codes faux avant d'invalider le jeton d'étape (il faut alors refaire le mot de passe)
 * @param dureeReauthentification   fenêtre ouverte par un code TOTP pour les actions les plus sensibles
 * @param dureeInvitation           validité du lien d'invitation d'un compte interne
 * @param conservationJournal       durée de conservation du journal des actions d'administration
 * @param tentativesParCompte       codes saisis (justes ou faux) par compte et par fenêtre, avant réponse 429
 * @param fenetreCompte             fenêtre de la limite par compte
 * @param tentativesParIp           codes saisis par IP et par fenêtre (large : plusieurs abonnés partagent une IP mobile)
 * @param fenetreIp                 fenêtre de la limite par IP
 */
@Validated
@ConfigurationProperties(prefix = "boutique.second-facteur")
public record ProprietesSecondFacteur(
        String cleChiffrement,
        String cleChiffrementPrecedente,
        @NotBlank @DefaultValue("Boutique informatique") String emetteur,
        String secretFixeDemo,
        @NotNull @DefaultValue("5m") Duration dureeEtape,
        @NotNull @DefaultValue("30m") Duration dureeEtapeMaximale,
        @Min(1) @DefaultValue("5") int essaisParEtape,
        @NotNull @DefaultValue("10m") Duration dureeReauthentification,
        @NotNull @DefaultValue("48h") Duration dureeInvitation,
        @NotNull @DefaultValue("1095d") Duration conservationJournal,
        @Min(1) @DefaultValue("10") int tentativesParCompte,
        @NotNull @DefaultValue("10m") Duration fenetreCompte,
        @Min(1) @DefaultValue("30") int tentativesParIp,
        @NotNull @DefaultValue("15m") Duration fenetreIp) {

    public boolean secretFixeDemoDefini() {
        return secretFixeDemo != null && !secretFixeDemo.isBlank();
    }
}
