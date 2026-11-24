package sn.boutique.commande.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Origine marketing de la commande (capturée à l'arrivée sur le site, phase 7) :
 * sert au tableau de bord « ventes par source » et aux conversions côté serveur.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class AttributionMarketing {

    @Column(length = 100)
    private String utmSource;

    @Column(length = 100)
    private String utmMedium;

    @Column(length = 150)
    private String utmCampagne;

    @Column(length = 150)
    private String utmContenu;

    @Column(length = 150)
    private String utmTerme;

    /** Identifiant de clic Google Ads (import des conversions hors ligne). */
    @Column(length = 255)
    private String gclid;

    /** Identifiant de clic Meta. */
    @Column(length = 255)
    private String fbclid;

    /** Cookie _fbp du pixel Meta. */
    @Column(length = 100)
    private String fbp;

    /** Cookie _fbc (construit à partir du fbclid). */
    @Column(length = 300)
    private String fbc;

    @Column(length = 500)
    private String pageArrivee;

    @Column(length = 500)
    private String referent;

    @Column(length = 45)
    private String adresseIp;

    @Column(length = 255)
    private String userAgent;
}
