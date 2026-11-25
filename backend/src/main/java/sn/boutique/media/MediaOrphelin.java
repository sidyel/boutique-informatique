package sn.boutique.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Fichier Cloudinary qui n'est plus référencé par le site mais dont la suppression a échoué après plusieurs
 * essais. Une tâche planifiée les supprimera (phase 9, ADR 0005 §1).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaOrphelin extends EntiteAuditee {

    @Column(nullable = false, length = 255, unique = true)
    private String idPublic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockageMedia.TypeRessource typeRessource;

    /** Ce qui a rendu le fichier orphelin (« suppression du média 42 du produit 7 », « compensation »…). */
    @Column(nullable = false, length = 200)
    private String origine;

    @Column(length = 500)
    private String derniereErreur;

    @Column(nullable = false)
    private int tentatives;

    MediaOrphelin(String idPublic, StockageMedia.TypeRessource typeRessource, String origine) {
        this.idPublic = idPublic;
        this.typeRessource = typeRessource;
        this.origine = tronquer(origine, 200);
    }

    void echec(int essais, String erreur) {
        this.tentatives += essais;
        this.derniereErreur = tronquer(erreur, 500);
    }

    private static String tronquer(String texte, int longueur) {
        return texte == null || texte.length() <= longueur ? texte : texte.substring(0, longueur);
    }
}
