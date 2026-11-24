package sn.boutique.marketing.modele;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Code de réduction saisi au panier. Le code est stocké en majuscules (saisie insensible à la casse).
 * <p>
 * {@code nombreUtilisations} est incrémenté de façon atomique à la validation de la commande (phase 4,
 * {@code UPDATE … WHERE nombre_utilisations < utilisations_max}) ; le nombre d'utilisations par client se
 * compte sur les commandes (code_promo + e-mail ou utilisateur).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodePromo extends EntiteAuditee {

    @Setter(AccessLevel.NONE)
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeCodePromo type;

    /** Pourcentage (1 à 100) ou montant en FCFA selon le type ; 0 pour la livraison offerte. */
    @Column(nullable = false)
    private long valeur;

    /** Sous-total minimum en FCFA (0 = aucun). */
    @Column(nullable = false)
    private long montantMinimum;

    private Instant dateDebut;

    private Instant dateFin;

    /** Nombre total d'utilisations autorisées (null = illimité). */
    private Integer utilisationsMax;

    /** Nombre d'utilisations par client (null = illimité). */
    private Integer utilisationsParClient;

    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private int nombreUtilisations;

    /** Catégories éligibles ; vide = tout le catalogue. */
    @ManyToMany
    @JoinTable(name = "code_promo_categorie",
            joinColumns = @JoinColumn(name = "code_promo_id"),
            inverseJoinColumns = @JoinColumn(name = "categorie_id"))
    private Set<Categorie> categoriesCiblees = new HashSet<>();

    @Column(nullable = false)
    private boolean actif = true;

    public CodePromo(String code, TypeCodePromo type, long valeur) {
        setCode(code);
        this.type = type;
        this.valeur = valeur;
    }

    public static String normaliserCode(String code) {
        return code == null ? null : code.strip().toUpperCase(Locale.ROOT);
    }

    public void setCode(String code) {
        this.code = normaliserCode(code);
    }

    /** Période et activation uniquement ; le plafond et le montant minimum sont vérifiés par le service. */
    public boolean estValideA(Instant instant) {
        return actif
                && (dateDebut == null || !instant.isBefore(dateDebut))
                && (dateFin == null || instant.isBefore(dateFin));
    }
}
