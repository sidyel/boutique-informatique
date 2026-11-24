package sn.boutique.marketing.modele;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.boutique.commande.modele.Commande;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Envoi d'une conversion côté serveur (API Conversions de Meta, conversions Google Ads).
 * La paire (plateforme, idEvenement) est unique : une conversion n'est jamais envoyée deux fois,
 * et {@code idEvenement} est le même que celui du pixel (dédoublonnage par la plateforme).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalConversion extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlateformeConversion plateforme;

    /** Nom de l'événement : Purchase, purchase… */
    @Column(nullable = false, length = 50)
    private String evenement;

    @Column(nullable = false, length = 100)
    private String idEvenement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutConversion statut = StatutConversion.EN_ATTENTE;

    /** Réponse brute de la plateforme (diagnostic). */
    @Column(columnDefinition = "text")
    private String reponse;

    @Column(nullable = false)
    private int nombreTentatives;

    /** Prochain essai (reprise avec délai croissant) ; null quand c'est terminé. */
    private Instant prochaineTentative;

    public JournalConversion(Commande commande, PlateformeConversion plateforme, String evenement, String idEvenement) {
        this.commande = commande;
        this.plateforme = plateforme;
        this.evenement = evenement;
        this.idEvenement = idEvenement;
    }
}
