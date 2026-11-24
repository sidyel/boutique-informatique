package sn.boutique.catalogue.modele;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Valeur d'une caractéristique pour un produit, éventuellement surchargée pour une variante
 * (par exemple la capacité de stockage). Une seule colonne de valeur est remplie selon le type.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValeurCaracteristique extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variante_id")
    private VarianteProduit variante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private DefinitionCaracteristique definition;

    @Column(length = 255)
    private String valeurTexte;

    @Column(precision = 14, scale = 4)
    private BigDecimal valeurNombre;

    private Boolean valeurBooleen;

    ValeurCaracteristique(Produit produit, DefinitionCaracteristique definition) {
        this.produit = produit;
        this.definition = definition;
    }
}
