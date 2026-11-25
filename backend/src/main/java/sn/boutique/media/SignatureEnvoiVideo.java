package sn.boutique.media;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Signature émise pour un envoi direct de vidéo. Sert à n'accepter une déclaration que pour le produit et
 * l'idPublic signés, une seule fois, et à retrouver les vidéos envoyées mais jamais déclarées (phase 9).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SignatureEnvoiVideo extends EntiteAuditee {

    @Column(nullable = false)
    private Long produitId;

    @Column(nullable = false, length = 255, unique = true)
    private String idPublic;

    @Column(nullable = false, length = 255)
    private String dossier;

    /** Au-delà, l'envoi ne peut plus commencer (signature refusée par Cloudinary). */
    @Column(nullable = false)
    private Instant dateExpiration;

    /** Date de la déclaration de la vidéo ; null tant qu'elle n'est pas déclarée. */
    private Instant dateUtilisation;

    public SignatureEnvoiVideo(Long produitId, String idPublic, Instant dateExpiration) {
        this.produitId = produitId;
        this.idPublic = idPublic;
        this.dossier = idPublic.substring(0, idPublic.lastIndexOf('/'));
        this.dateExpiration = dateExpiration;
    }

    public boolean estUtilisee() {
        return dateUtilisation != null;
    }

    public void utiliser(Instant date) {
        this.dateUtilisation = date;
    }
}
