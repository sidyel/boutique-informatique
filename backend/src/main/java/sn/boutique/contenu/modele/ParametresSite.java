package sn.boutique.contenu.modele;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import sn.boutique.noyau.telephone.TelephoneSenegal;

/**
 * Paramètres du site, modifiables depuis l'admin sans redéploiement. Une seule ligne (id = 1,
 * imposé par une contrainte CHECK et créée par la migration).
 * <p>
 * Seuls des identifiants <strong>publics</strong> sont stockés ici (GTM, GA4, pixel Meta…).
 * Les secrets (jeton de l'API Conversions, clés de paiement) restent dans les variables d'environnement.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ParametresSite {

    public static final long ID_UNIQUE = 1L;

    @Id
    @Setter(AccessLevel.NONE)
    private Long id = ID_UNIQUE;

    @Column(nullable = false, length = 80)
    private String nomSite;

    @Column(length = 500)
    private String logoUrl;

    @Column(length = 500)
    private String logoSombreUrl;

    @Column(length = 500)
    private String faviconUrl;

    /** Image Open Graph par défaut (1200 × 630). */
    @Column(length = 500)
    private String imagePartageUrl;

    /* Identifiants des fichiers sur le stockage de médias : pour supprimer l'ancien fichier remplacé. */
    @Column(length = 255)
    private String logoIdPublic;

    @Column(length = 255)
    private String logoSombreIdPublic;

    @Column(length = 255)
    private String faviconIdPublic;

    @Column(length = 255)
    private String imagePartageIdPublic;

    /** Format E.164. */
    @Setter(AccessLevel.NONE)
    @Column(length = 13)
    private String telephone;

    /** Numéro WhatsApp, format E.164. */
    @Setter(AccessLevel.NONE)
    @Column(length = 13)
    private String whatsapp;

    @Column(length = 254)
    private String email;

    @Column(length = 255)
    private String adresse;

    /** « Lun.–sam. 9 h–19 h ». */
    @Column(length = 255)
    private String horaires;

    /** {"facebook": "https://…", "instagram": "…", "tiktok": "…"}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> liensReseauxSociaux = new LinkedHashMap<>();

    @Column(length = 20)
    private String idGoogleTagManager;

    @Column(length = 20)
    private String idGa4;

    @Column(length = 20)
    private String idGoogleAds;

    @Column(length = 100)
    private String libelleConversionAchat;

    @Column(length = 20)
    private String idPixelMeta;

    @Column(length = 100)
    private String codeVerificationGoogle;

    @Column(length = 100)
    private String codeVerificationMeta;

    /**
     * Seuls les clients ayant reçu le produit (commande LIVREE) peuvent publier un avis
     * (ADR 0004 §5). Désactivable pour ouvrir les avis à tous les clients connectés.
     */
    @Column(nullable = false)
    private boolean avisReservesAuxAcheteurs = true;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant dateModification;

    public void setTelephone(String telephone) {
        this.telephone = TelephoneSenegal.normaliserFacultatif(telephone);
    }

    public void setWhatsapp(String whatsapp) {
        this.whatsapp = TelephoneSenegal.normaliserFacultatif(whatsapp);
    }
}
