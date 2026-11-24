package sn.boutique.catalogue.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Marque (fabricant) : Asus, Lenovo, HP…
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Marque extends EntiteAuditee {

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    private String logoUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 70)
    private String metaTitre;

    @Column(length = 170)
    private String metaDescription;

    public Marque(String nom, String slug) {
        this.nom = nom;
        this.slug = slug;
    }
}
