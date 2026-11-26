package sn.boutique.contenu.modele;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** Barre supérieure de l'en-tête : « Livraison à Dakar en 24 h ». Vide : rien n'est affiché. */
    @Column(length = 120)
    private String messageBarreSuperieure;

    /** Bandeau de confiance de l'accueil (6 au plus). Vide : bandeau masqué. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<ElementConfiance> elementsConfiance = new ArrayList<>();

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

    /** Bouton d'achat mis en avant sur la fiche produit (V14). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BoutonPrincipalFiche boutonPrincipalFiche = BoutonPrincipalFiche.PANIER;

    /**
     * Délai affiché près du bouton d'achat : « Livré à Dakar en 24 h, en régions en 2 à 4 jours ».
     * Vide : rien n'est affiché. Jamais affiché pour une variante en rupture de stock.
     */
    @Column(length = 120)
    private String delaiLivraison;

    /* --- Politique de retour (JSON-LD MerchantReturnPolicy, V15) --- */

    /** Faux : « retours refusés » (MerchantReturnNotPermitted). */
    @Column(nullable = false)
    private boolean retoursAcceptes = true;

    /** Délai de retour en jours, obligatoire si les retours sont acceptés. */
    private Integer delaiRetourJours = 7;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FraisRetour fraisRetour = FraisRetour.GRATUIT;

    @Column(nullable = false)
    private boolean retourEnBoutique = true;

    /** Reprise par un livreur (JSON-LD ReturnByMail). */
    @Column(nullable = false)
    private boolean retourParLivreur;

    /* --- Boutique physique (JSON-LD Store, V15) --- */

    /** Vrai : la page publie un Store ; impose rue, ville et région (contrainte en base). */
    @Column(nullable = false)
    private boolean boutiquePhysique;

    @Column(length = 200)
    private String boutiqueRue;

    @Column(length = 100)
    private String boutiqueVille;

    /** Région administrative : « Dakar », « Thiès ». */
    @Column(length = 100)
    private String boutiqueRegion;

    @Column(length = 10)
    private String boutiqueCodePostal;

    @Column(precision = 9, scale = 6)
    private BigDecimal boutiqueLatitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal boutiqueLongitude;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<HoraireOuverture> horairesOuverture = new ArrayList<>();

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
