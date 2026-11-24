package sn.boutique.panier.modele;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Panier d'un client connecté (utilisateur) ou d'un visiteur (jeton invité en cookie).
 * Le panier invité est fusionné avec celui du compte à la connexion (phase 4).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Panier extends EntiteAuditee {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", unique = true)
    private Utilisateur utilisateur;

    @Column(unique = true, length = 64)
    private String jetonInvite;

    @OneToMany(mappedBy = "panier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dateCreation ASC")
    private List<LignePanier> lignes = new ArrayList<>();

    public static Panier pourUtilisateur(Utilisateur utilisateur) {
        Panier panier = new Panier();
        panier.utilisateur = utilisateur;
        return panier;
    }

    public static Panier pourInvite(String jetonInvite) {
        Panier panier = new Panier();
        panier.jetonInvite = jetonInvite;
        return panier;
    }

    public Optional<LignePanier> ligne(VarianteProduit variante) {
        return lignes.stream().filter(ligne -> ligne.getVariante().equals(variante)).findFirst();
    }

    /** Ajoute la quantité à la ligne existante ou crée une ligne. Le contrôle du stock est fait par le service. */
    public LignePanier ajouter(VarianteProduit variante, int quantite) {
        return ligne(variante)
                .map(existante -> {
                    existante.setQuantite(existante.getQuantite() + quantite);
                    return existante;
                })
                .orElseGet(() -> {
                    LignePanier nouvelle = new LignePanier(this, variante, quantite);
                    lignes.add(nouvelle);
                    return nouvelle;
                });
    }
}
