package sn.boutique.catalogue.modele;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Déclinaison vendable d'un produit (« 16 Go / 512 Go / Gris »).
 * Le SKU est l'identifiant utilisé dans les flux produits et le suivi publicitaire.
 * Le verrou optimiste ({@code version}) protège le stock contre les écritures concurrentes.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VarianteProduit extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 150)
    private String libelle;

    /** Attributs de déclinaison : {"couleur": "Gris", "ram": "16 Go"}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> attributs = new LinkedHashMap<>();

    /** Prix en FCFA. */
    @Column(nullable = false)
    private long prix;

    /** Prix habituel en FCFA, affiché barré (facultatif). */
    private Long prixBarre;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private int seuilAlerte = 2;

    private Integer poidsGrammes;

    @Column(nullable = false)
    private boolean actif = true;

    @Version
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private long version;

    VarianteProduit(Produit produit, String sku, String libelle, long prix, int stock) {
        this.produit = produit;
        this.sku = sku;
        this.libelle = libelle;
        this.prix = prix;
        this.stock = stock;
    }
}
