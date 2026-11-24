package sn.boutique.commande.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Ligne de commande : copie figée du produit au moment de l'achat (nom, SKU, libellé, image, prix).
 * <p>
 * La variante reste référencée avec {@code ON DELETE RESTRICT} : un produit déjà commandé
 * n'est jamais supprimé physiquement, il est désactivé (voir docs/decisions/0004).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LigneCommande extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private VarianteProduit variante;

    @Column(nullable = false, length = 200)
    private String nomProduit;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 150)
    private String libelleVariante;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private long prixUnitaire;

    @Column(nullable = false)
    private int quantite;

    @Column(nullable = false)
    private long totalLigne;

    LigneCommande(Commande commande, VarianteProduit variante, String imageUrl, int quantite) {
        this.commande = commande;
        this.variante = variante;
        this.nomProduit = variante.getProduit().getNom();
        this.sku = variante.getSku();
        this.libelleVariante = variante.getLibelle();
        this.imageUrl = imageUrl;
        this.prixUnitaire = variante.getPrix();
        this.quantite = quantite;
        this.totalLigne = Math.multiplyExact(prixUnitaire, (long) quantite);
    }
}
