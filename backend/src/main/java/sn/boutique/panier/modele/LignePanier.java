package sn.boutique.panier.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Ligne de panier. Aucun prix n'est stocké : il est toujours relu sur la variante.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LignePanier extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "panier_id", nullable = false)
    private Panier panier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private VarianteProduit variante;

    @Setter
    @Column(nullable = false)
    private int quantite;

    LignePanier(Panier panier, VarianteProduit variante, int quantite) {
        this.panier = panier;
        this.variante = variante;
        this.quantite = quantite;
    }
}
