package sn.boutique.noyau.alerte;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alertes à l'équipe (préfixe {@code boutique.alertes}).
 *
 * @param destinataire adresse qui reçoit les alertes par e-mail (EMAIL_ALERTES) ; vide : journaux seulement
 */
@ConfigurationProperties(prefix = "boutique.alertes")
public record ProprietesAlertes(String destinataire) {
}
