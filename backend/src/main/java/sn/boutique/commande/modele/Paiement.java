package sn.boutique.commande.modele;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Tentative de paiement d'une commande. La paire (fournisseur, referenceExterne) est unique :
 * un webhook reçu deux fois ne crée jamais deux paiements (idempotence).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Paiement extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeFournisseurPaiement fournisseur;

    @Column(length = 120)
    private String referenceExterne;

    /** Montant en FCFA, revérifié contre le total de la commande à la réception du webhook. */
    @Column(nullable = false)
    private long montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutPaiement statut = StatutPaiement.EN_ATTENTE;

    /** Page de paiement du prestataire (redirection du client) ; absente pour le paiement à la livraison. */
    @Column(length = 500)
    private String urlPaiement;

    /** Corps JSON brut du dernier webhook reçu (audit). */
    @JdbcTypeCode(SqlTypes.JSON)
    private String donneesBrutes;

    public Paiement(Commande commande, TypeFournisseurPaiement fournisseur, long montant) {
        this.commande = commande;
        this.fournisseur = fournisseur;
        this.montant = montant;
    }
}
