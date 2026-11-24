package sn.boutique.catalogue.modele;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Catégorie du catalogue, organisée en arbre (« PC portables » → « PC portables gaming »).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Categorie extends EntiteAuditee {

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(nullable = false, unique = true, length = 140)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Categorie parent;

    @OneToMany(mappedBy = "parent")
    @OrderBy("position ASC, nom ASC")
    private List<Categorie> enfants = new ArrayList<>();

    @Column(columnDefinition = "text")
    private String description;

    /** Tuile de la page d'accueil. */
    private String imageUrl;

    /** En-tête de la page catégorie. */
    private String banniereUrl;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(length = 70)
    private String metaTitre;

    @Column(length = 170)
    private String metaDescription;

    /** Texte éditorial affiché en bas de la page catégorie (SEO). */
    @Column(columnDefinition = "text")
    private String texteSeo;

    /** Valeur de google_product_category pour les flux Merchant Center. */
    @Column(length = 255)
    private String categorieGoogle;

    public Categorie(String nom, String slug) {
        this.nom = nom;
        this.slug = slug;
    }
}
