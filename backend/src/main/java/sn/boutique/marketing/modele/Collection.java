package sn.boutique.marketing.modele;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Page d'atterrissage de campagne ({@code /promo/black-friday}) : sélection ordonnée de produits.
 * Une collection peut être exclue de l'indexation (page réservée aux publicités).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Collection extends EntiteAuditee {

    @Column(nullable = false, length = 150)
    private String titre;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 500)
    private String banniereOrdinateurUrl;

    @Column(length = 500)
    private String banniereMobileUrl;

    /** Produits dans l'ordre choisi par le marketing. */
    @ManyToMany
    @JoinTable(name = "collection_produit",
            joinColumns = @JoinColumn(name = "collection_id"),
            inverseJoinColumns = @JoinColumn(name = "produit_id"))
    @OrderColumn(name = "position")
    private List<Produit> produits = new ArrayList<>();

    /** false : balise noindex et absente du sitemap. */
    @Column(nullable = false)
    private boolean indexable = true;

    @Column(nullable = false)
    private boolean actif = true;

    private Instant dateDebut;

    private Instant dateFin;

    @Column(length = 70)
    private String metaTitre;

    @Column(length = 170)
    private String metaDescription;

    public Collection(String titre, String slug) {
        this.titre = titre;
        this.slug = slug;
    }
}
