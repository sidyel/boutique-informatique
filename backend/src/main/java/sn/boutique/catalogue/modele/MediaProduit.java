package sn.boutique.catalogue.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Image ou vidéo d'un produit, stockée sur Cloudinary (la base ne garde que l'URL et l'idPublic).
 * Une seule image principale par produit (index unique partiel en base).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaProduit extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    /** Photo propre à une variante (une couleur, par exemple). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variante_id")
    private VarianteProduit variante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeMedia type;

    @Column(nullable = false, length = 500)
    private String url;

    /** Identifiant Cloudinary, nécessaire pour supprimer le fichier. */
    @Column(length = 255)
    private String idPublic;

    /** Image d'aperçu d'une vidéo. */
    @Column(length = 500)
    private String urlMiniature;

    @Column(nullable = false, length = 255)
    private String texteAlternatif;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean principal;

    MediaProduit(Produit produit, TypeMedia type, String url, String texteAlternatif, int position) {
        this.produit = produit;
        this.type = type;
        this.url = url;
        this.texteAlternatif = texteAlternatif;
        this.position = position;
    }
}
