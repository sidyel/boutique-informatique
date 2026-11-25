package sn.boutique.catalogue.modele;

import java.math.BigDecimal;
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
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Produit du catalogue. Le prix et le stock sont portés par ses variantes.
 * <p>
 * {@code prixMinimum} et {@code stockTotal} sont des agrégats des variantes actives,
 * recalculés par un trigger PostgreSQL à chaque modification de variante (voir V2) :
 * ils servent aux tris et filtres sans jointure, et JPA ne les écrit jamais.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Produit extends EntiteAuditee {

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marque_id", nullable = false)
    private Marque marque;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categorie_id", nullable = false)
    private Categorie categorie;

    @Column(length = 500)
    private String descriptionCourte;

    /** HTML nettoyé à l'écriture (OWASP Java HTML Sanitizer, avec l'admin des produits en phase 6). */
    @Column(columnDefinition = "text")
    private String descriptionLongue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EtatProduit etat = EtatProduit.NEUF;

    @Column(nullable = false)
    private int garantieMois;

    /** Code EAN/UPC. */
    @Column(length = 14)
    private String gtin;

    /** Référence fabricant. */
    @Column(length = 70)
    private String mpn;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(nullable = false)
    private boolean misEnAvant;

    @Column(length = 70)
    private String metaTitre;

    @Column(length = 170)
    private String metaDescription;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal noteMoyenne = BigDecimal.ZERO;

    @Column(nullable = false)
    private int nombreAvis;

    /** Unités vendues (tri « popularité »), incrémenté à la confirmation du paiement (phase 4). */
    @Column(nullable = false)
    private int nombreVentes;

    /*
     * ATTENTION — valeurs périmées dans la transaction (prixMinimum, stockTotal).
     * Ces deux champs sont calculés par le trigger PostgreSQL, jamais par Hibernate. Un Produit déjà
     * chargé garde donc les anciennes valeurs après la modification d'une de ses variantes, même
     * après un flush. Tout service qui modifie des variantes puis renvoie le produit (phase 2 :
     * catalogue, phase 6 : admin, stock) doit le relire avant de répondre :
     *     produitDepot.saveAndFlush(produit); entityManager.refresh(produit);
     * ou une nouvelle requête après flush. Un test de service vérifie ce cas (voir ADR 0004).
     */

    /** Prix de la variante active la moins chère (null si aucune). Maintenu par trigger : voir ci-dessus. */
    @Setter(AccessLevel.NONE)
    @Column(insertable = false, updatable = false)
    private Long prixMinimum;

    /** Somme des stocks des variantes actives. Maintenu par trigger : voir ci-dessus. */
    @Setter(AccessLevel.NONE)
    @Column(insertable = false, updatable = false)
    private Integer stockTotal;

    /**
     * Retirer une variante de cette liste la supprime (orphanRemoval). Une variante déjà commandée
     * ne peut pas l'être (clé étrangère ligne_commande en ON DELETE RESTRICT) : on la désactive.
     */
    @OneToMany(mappedBy = "produit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("prix ASC")
    private List<VarianteProduit> variantes = new ArrayList<>();

    @OneToMany(mappedBy = "produit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<MediaProduit> medias = new ArrayList<>();

    @OneToMany(mappedBy = "produit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ValeurCaracteristique> caracteristiques = new ArrayList<>();

    public Produit(String nom, String slug, Marque marque, Categorie categorie) {
        this.nom = nom;
        this.slug = slug;
        this.marque = marque;
        this.categorie = categorie;
    }

    public VarianteProduit ajouterVariante(String sku, String libelle, long prix, int stock) {
        VarianteProduit variante = new VarianteProduit(this, sku, libelle, prix, stock);
        variantes.add(variante);
        return variante;
    }

    public MediaProduit ajouterMedia(TypeMedia type, String url, String texteAlternatif) {
        MediaProduit media = new MediaProduit(this, type, url, texteAlternatif, medias.size());
        if (medias.isEmpty() && type == TypeMedia.IMAGE) {
            media.setPrincipal(true);
        }
        medias.add(media);
        return media;
    }

    /**
     * Ajoute une valeur de caractéristique au produit.
     * <p>
     * ATTENTION pour une valeur propre à une variante ({@code valeur.setVariante(…)}) : la variante doit
     * déjà être en base. Hibernate propage l'enregistrement aux collections dans l'ordre alphabétique
     * (caracteristiques avant variantes) : une variante nouvelle serait référencée par NULL au moment de
     * l'INSERT, et deux valeurs de variantes différentes entreraient en collision sur
     * {@code uk_valeur_caracteristique}. Enregistrer d'abord le produit et ses variantes.
     */
    public ValeurCaracteristique ajouterCaracteristique(DefinitionCaracteristique definition) {
        ValeurCaracteristique valeur = new ValeurCaracteristique(this, definition);
        caracteristiques.add(valeur);
        return valeur;
    }
}
