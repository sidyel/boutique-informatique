package sn.boutique.contenu.modele;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Article de blog ou guide d'achat (« Quel PC portable pour un étudiant à Dakar ? »),
 * relié à des produits et à une catégorie du catalogue (maillage interne pour le SEO).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article extends EntiteAuditee {

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(length = 500)
    private String extrait;

    /** HTML nettoyé (OWASP Java HTML Sanitizer). */
    @Column(columnDefinition = "text")
    private String contenu;

    @Column(length = 500)
    private String imageCouverture;

    /** Null si le compte de l'auteur a été supprimé : on affiche alors {@code nomAuteur}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id")
    private Utilisateur auteur;

    /** Nom affiché (copie, pour le JSON-LD Article). */
    @Column(nullable = false, length = 120)
    private String nomAuteur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private Categorie categorie;

    @ManyToMany
    @JoinTable(name = "article_produit",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "produit_id"))
    @OrderColumn(name = "position")
    private List<Produit> produitsLies = new ArrayList<>();

    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private boolean publie;

    @Setter(AccessLevel.NONE)
    private Instant datePublication;

    @Column(length = 70)
    private String metaTitre;

    @Column(length = 170)
    private String metaDescription;

    public Article(String titre, String slug, String nomAuteur) {
        this.titre = titre;
        this.slug = slug;
        this.nomAuteur = nomAuteur;
    }

    /** Publie l'article ; la date de première publication est conservée lors d'une republication. */
    public void publier(Instant maintenant) {
        this.publie = true;
        if (datePublication == null) {
            this.datePublication = maintenant;
        }
    }

    public void depublier() {
        this.publie = false;
    }
}
