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
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Trace d'un changement de statut (la date est {@code dateCreation}).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HistoriqueStatutCommande extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    /** Null pour la création de la commande. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private StatutCommande ancienStatut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatutCommande nouveauStatut;

    /** E-mail de l'auteur, ou « SYSTEME » (webhook, tâche planifiée). */
    @Column(nullable = false, length = 254)
    private String auteur;

    @Column(columnDefinition = "text")
    private String commentaire;

    HistoriqueStatutCommande(Commande commande, StatutCommande ancienStatut, StatutCommande nouveauStatut,
                             String auteur, String commentaire) {
        this.commande = commande;
        this.ancienStatut = ancienStatut;
        this.nouveauStatut = nouveauStatut;
        this.auteur = auteur;
        this.commentaire = commentaire;
    }
}
