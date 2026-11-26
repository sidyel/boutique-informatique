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
 * Ligne de panier. Le prix facturé est toujours relu sur la variante ; {@code prixVu} est seulement le prix
 * que le client a vu (à l'ajout ou à sa dernière acceptation) : s'il diffère, le client est prévenu avant de
 * payer (V16).
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

    @Column(nullable = false)
    private long prixVu;

    LignePanier(Panier panier, VarianteProduit variante, int quantite) {
        this.panier = panier;
        this.variante = variante;
        this.quantite = quantite;
        this.prixVu = variante.getPrix();
    }

    /** Le client a vu (ou accepté) le prix actuel de la variante. */
    public void accepterPrixActuel() {
        this.prixVu = variante.getPrix();
    }

    /** Fusion d'un panier invité : la ligne garde le prix que le client avait vu. */
    public void restaurerPrixVu(long prix) {
        this.prixVu = prix;
    }

    public boolean prixModifie() {
        return prixVu != variante.getPrix();
    }
}
