package sn.boutique.apresvente.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Message du fil d'une demande SAV. Une note interne n'est jamais montrée au client.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageSav extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demande_id", nullable = false)
    private DemandeSav demande;

    /** Null si le compte a été supprimé. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id")
    private Utilisateur auteur;

    @Column(nullable = false, length = 120)
    private String nomAuteur;

    /** true : message d'un membre de l'équipe ; false : message du client. */
    @Column(nullable = false)
    private boolean deLaBoutique;

    @Column(nullable = false)
    private boolean noteInterne;

    @Column(nullable = false, columnDefinition = "text")
    private String contenu;

    MessageSav(DemandeSav demande, Utilisateur auteur, String nomAuteur, boolean deLaBoutique,
               boolean noteInterne, String contenu) {
        this.demande = demande;
        this.auteur = auteur;
        this.nomAuteur = nomAuteur;
        this.deLaBoutique = deLaBoutique;
        this.noteInterne = noteInterne;
        this.contenu = contenu;
    }
}
