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
 * Caractéristique technique propre à une catégorie (« processeur_modele », « ram_go », « socket »).
 * Sert au tableau de caractéristiques, aux filtres dynamiques, au comparateur et au configurateur.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefinitionCaracteristique extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categorie_id", nullable = false)
    private Categorie categorie;

    /** Identifiant technique stable, en snake_case : « processeur_modele ». */
    @Column(nullable = false, length = 60)
    private String cle;

    @Column(nullable = false, length = 100)
    private String libelle;

    /** Unité affichée : Go, Hz, W, pouces… */
    @Column(length = 20)
    private String unite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeCaracteristique type;

    /** Section du tableau de caractéristiques : « Processeur », « Écran », « Connectique ». */
    @Column(nullable = false, length = 60)
    private String groupe;

    @Column(nullable = false)
    private boolean filtrable;

    /** Incluse dans le vecteur de recherche (processeur, carte graphique…). */
    @Column(nullable = false)
    private boolean cleRecherche;

    @Column(nullable = false)
    private int ordre;

    /**
     * Position sur la carte produit (1 à 4, unique dans la catégorie) ; {@code null} : fiche produit seulement.
     */
    private Short ordreCarte;

    /** Libellé de la carte, plus court que {@link #libelle} : « RAM » pour « Mémoire vive ». */
    @Column(length = 20)
    private String libelleCourt;

    public DefinitionCaracteristique(
            Categorie categorie, String cle, String libelle, TypeCaracteristique type, String groupe) {
        this.categorie = categorie;
        this.cle = cle;
        this.libelle = libelle;
        this.type = type;
        this.groupe = groupe;
    }
}
