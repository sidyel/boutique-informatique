package sn.boutique.compte.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.boutique.noyau.audit.EntiteAuditee;
import sn.boutique.noyau.telephone.TelephoneSenegal;

/**
 * Adresse enregistrée dans le carnet d'un client. Au Sénégal, le quartier et le point de repère
 * (« derrière la pharmacie Mame Diarra ») comptent souvent plus que le nom de rue.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Adresse extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /** « Maison », « Bureau »… */
    @Column(nullable = false, length = 40)
    private String libelle;

    @Column(nullable = false, length = 120)
    private String nomComplet;

    /** Format E.164 : +221XXXXXXXXX. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 13)
    private String telephone;

    @Column(nullable = false, length = 80)
    private String ville;

    @Column(nullable = false, length = 80)
    private String quartier;

    @Column(length = 255)
    private String adresseLigne;

    @Column(length = 255)
    private String pointDeRepere;

    @Column(nullable = false)
    private boolean parDefaut;

    public Adresse(Utilisateur utilisateur, String libelle, String nomComplet, String telephone,
                   String ville, String quartier) {
        this.utilisateur = utilisateur;
        this.libelle = libelle;
        this.nomComplet = nomComplet;
        setTelephone(telephone);
        this.ville = ville;
        this.quartier = quartier;
    }

    public void setTelephone(String telephone) {
        this.telephone = TelephoneSenegal.normaliserOuEchouer(telephone);
    }
}
