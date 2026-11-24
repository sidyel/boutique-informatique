package sn.boutique.marketing.modele;

import java.time.Instant;

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
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Bannière programmable, avec une image distincte pour ordinateur et pour mobile
 * (la majorité du trafic publicitaire arrive sur téléphone).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banniere extends EntiteAuditee {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EmplacementBanniere emplacement;

    /** Catégorie concernée, pour l'emplacement ENTETE_CATEGORIE. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private Categorie categorie;

    @Column(nullable = false, length = 500)
    private String imageOrdinateurUrl;

    @Column(nullable = false, length = 500)
    private String imageMobileUrl;

    /** Texte alternatif de l'image (accessibilité, SEO des images). */
    @Column(nullable = false, length = 255)
    private String texteAlternatif;

    @Column(length = 120)
    private String titre;

    @Column(length = 255)
    private String sousTitre;

    @Column(length = 40)
    private String texteBouton;

    /** Chemin interne (« /categorie/pc-portables ») ou URL complète. */
    @Column(length = 500)
    private String lien;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean actif = true;

    private Instant dateDebut;

    private Instant dateFin;

    public Banniere(EmplacementBanniere emplacement, String imageOrdinateurUrl, String imageMobileUrl,
                    String texteAlternatif) {
        this.emplacement = emplacement;
        this.imageOrdinateurUrl = imageOrdinateurUrl;
        this.imageMobileUrl = imageMobileUrl;
        this.texteAlternatif = texteAlternatif;
    }

    public boolean estVisibleA(Instant instant) {
        return actif
                && (dateDebut == null || !instant.isBefore(dateDebut))
                && (dateFin == null || instant.isBefore(dateFin));
    }
}
