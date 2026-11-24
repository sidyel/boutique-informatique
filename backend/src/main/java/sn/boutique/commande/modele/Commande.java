package sn.boutique.commande.modele;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.audit.EntiteAuditee;
import sn.boutique.noyau.telephone.TelephoneSenegal;

/**
 * Commande d'un client connecté ou invité. Les montants sont recalculés côté serveur
 * (le frontend n'envoie jamais de prix) ; la base vérifie que total = sousTotal + frais − remise.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Commande extends EntiteAuditee {

    /** Numéro lisible : CMD-2026-000123 (voir {@link NumeroCommande}). */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    /** Null pour une commande passée en invité. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @Column(nullable = false, length = 254)
    private String emailClient;

    /** Format E.164 : +221XXXXXXXXX. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 13)
    private String telephoneClient;

    /** Copie figée ; absente pour un retrait en magasin. */
    @Embedded
    private AdresseLivraison adresseLivraison;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_livraison_id")
    private ZoneLivraison zoneLivraison;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModeLivraison modeLivraison;

    @Column(nullable = false)
    private long sousTotal;

    @Column(nullable = false)
    private long fraisLivraison;

    @Column(nullable = false)
    private long remise;

    @Column(nullable = false)
    private long total;

    @Column(length = 40)
    private String codePromo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModePaiement modePaiement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatutCommande statut = StatutCommande.EN_ATTENTE_PAIEMENT;

    @Embedded
    private AttributionMarketing attribution = new AttributionMarketing();

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LigneCommande> lignes = new ArrayList<>();

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL)
    @OrderBy("dateCreation ASC")
    private List<HistoriqueStatutCommande> historique = new ArrayList<>();

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL)
    @OrderBy("dateCreation ASC")
    private List<Paiement> paiements = new ArrayList<>();

    public Commande(String numero, String emailClient, String telephoneClient,
                    ModeLivraison modeLivraison, ModePaiement modePaiement) {
        this.numero = numero;
        this.emailClient = emailClient;
        setTelephoneClient(telephoneClient);
        this.modeLivraison = modeLivraison;
        this.modePaiement = modePaiement;
    }

    public void setTelephoneClient(String telephone) {
        this.telephoneClient = TelephoneSenegal.normaliserOuEchouer(telephone);
    }

    /**
     * Ajoute une ligne avec une copie figée du produit (nom, SKU, libellé, image, prix).
     * La variante reste référencée : une variante commandée ne peut plus être supprimée.
     */
    public LigneCommande ajouterLigne(VarianteProduit variante, String imageUrl, int quantite) {
        LigneCommande ligne = new LigneCommande(this, variante, imageUrl, quantite);
        lignes.add(ligne);
        return ligne;
    }

    /** Recalcule sous-total et total à partir des lignes, des frais et de la remise. */
    public void recalculerTotaux() {
        this.sousTotal = lignes.stream().mapToLong(LigneCommande::getTotalLigne).sum();
        this.total = Math.max(0, sousTotal + fraisLivraison - remise);
    }

    /** Change le statut et garde la trace du changement. */
    public HistoriqueStatutCommande changerStatut(StatutCommande nouveau, String auteur, String commentaire) {
        HistoriqueStatutCommande entree = new HistoriqueStatutCommande(this, statut, nouveau, auteur, commentaire);
        historique.add(entree);
        this.statut = nouveau;
        return entree;
    }
}
