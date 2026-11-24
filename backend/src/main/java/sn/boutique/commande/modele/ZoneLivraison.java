package sn.boutique.commande.modele;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Zone de livraison avec son tarif : « Dakar Plateau », « Banlieue », « Thiès »…
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneLivraison extends EntiteAuditee {

    @Column(nullable = false, unique = true, length = 80)
    private String nom;

    /** Villes et quartiers couverts : ["Plateau", "Médina", "Fann"]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> villesQuartiers = new ArrayList<>();

    /** Frais de livraison en FCFA. */
    @Column(nullable = false)
    private long tarif;

    @Column(nullable = false)
    private int delaiJours;

    @Column(nullable = false)
    private boolean actif = true;

    public ZoneLivraison(String nom, long tarif, int delaiJours) {
        this.nom = nom;
        this.tarif = tarif;
        this.delaiJours = delaiJours;
    }
}
