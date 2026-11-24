package sn.boutique.contenu.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Page de contenu fixe : CGV, mentions légales, confidentialité, livraison, retours et garantie,
 * à propos, contact.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PageStatique extends EntiteAuditee {

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 150)
    private String titre;

    /** HTML nettoyé. */
    @Column(nullable = false, columnDefinition = "text")
    private String contenu;

    @Column(length = 70)
    private String metaTitre;

    @Column(length = 170)
    private String metaDescription;

    public PageStatique(String slug, String titre, String contenu) {
        this.slug = slug;
        this.titre = titre;
        this.contenu = contenu;
    }
}
