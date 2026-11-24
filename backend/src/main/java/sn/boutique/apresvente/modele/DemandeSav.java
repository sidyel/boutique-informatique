package sn.boutique.apresvente.modele;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.LigneCommande;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Demande d'après-vente (panne, produit non conforme, rétractation, garantie) sur une commande,
 * éventuellement sur une ligne précise, avec un fil de messages entre le client et la boutique.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemandeSav extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ligne_commande_id")
    private LigneCommande ligneCommande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MotifSav motif;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> photos = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutSav statut = StatutSav.OUVERTE;

    @OneToMany(mappedBy = "demande", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dateCreation ASC")
    private List<MessageSav> messages = new ArrayList<>();

    public DemandeSav(Commande commande, MotifSav motif, String description) {
        this.commande = commande;
        this.motif = motif;
        this.description = description;
    }

    public MessageSav ajouterMessage(Utilisateur auteur, String nomAuteur, boolean deLaBoutique,
                                     boolean noteInterne, String contenu) {
        MessageSav message = new MessageSav(this, auteur, nomAuteur, deLaBoutique, noteInterne, contenu);
        messages.add(message);
        return message;
    }
}
