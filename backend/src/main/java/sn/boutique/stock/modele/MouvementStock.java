package sn.boutique.stock.modele;

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
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.modele.Commande;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Journal immuable des variations de stock d'une variante : entrée fournisseur, vente,
 * ajustement d'inventaire, retour client. {@code quantite} est signée (+ entrée, − sortie).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MouvementStock extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private VarianteProduit variante;

    @Column(nullable = false)
    private int quantite;

    /** Stock de la variante après ce mouvement (lecture de l'historique sans recalcul). */
    @Column(nullable = false)
    private int stockApres;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeMouvementStock type;

    @Column(length = 255)
    private String motif;

    /** Commande à l'origine d'une sortie ou d'un retour. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id")
    private Commande commande;

    /** E-mail de l'auteur, ou « SYSTEME ». */
    @Column(nullable = false, length = 254)
    private String auteur;

    public MouvementStock(VarianteProduit variante, int quantite, int stockApres, TypeMouvementStock type,
                          String motif, Commande commande, String auteur) {
        this.variante = variante;
        this.quantite = quantite;
        this.stockApres = stockApres;
        this.type = type;
        this.motif = motif;
        this.commande = commande;
        this.auteur = auteur;
    }
}
