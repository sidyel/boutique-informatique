package sn.boutique.apresvente.modele;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Avis client sur un produit, publié après modération.
 * <p>
 * Les avis approuvés alimentent {@code produit.note_moyenne} et {@code produit.nombre_avis}
 * par un trigger (V6) : même mise en garde que pour prixMinimum (produit déjà chargé = valeurs
 * périmées, voir ADR 0004).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Avis extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    /** Null si le compte a été supprimé : l'avis reste, signé « Client supprimé ». */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    /** Nom affiché : « Awa D. ». */
    @Column(nullable = false, length = 80)
    private String nomAuteur;

    @Column(nullable = false)
    private int note;

    @Column(length = 120)
    private String titre;

    @Column(columnDefinition = "text")
    private String commentaire;

    /** URL Cloudinary des photos jointes. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> photos = new ArrayList<>();

    /** Le client a acheté et reçu ce produit (commande livrée). */
    @Column(nullable = false)
    private boolean achatVerifie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutAvis statut = StatutAvis.EN_ATTENTE;

    @Column(columnDefinition = "text")
    private String reponseBoutique;

    private Instant dateReponse;

    public Avis(Produit produit, Utilisateur utilisateur, String nomAuteur, int note) {
        this.produit = produit;
        this.utilisateur = utilisateur;
        this.nomAuteur = nomAuteur;
        this.note = note;
    }

    public void repondre(String reponse, Instant maintenant) {
        this.reponseBoutique = reponse;
        this.dateReponse = maintenant;
    }
}
