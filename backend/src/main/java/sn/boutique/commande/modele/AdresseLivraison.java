package sn.boutique.commande.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.boutique.compte.modele.Adresse;
import sn.boutique.noyau.telephone.TelephoneSenegal;

/**
 * Copie figée de l'adresse de livraison au moment de la commande : modifier ou supprimer
 * une adresse du carnet ne change jamais une commande passée.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdresseLivraison {

    @Column(name = "livraison_nom_complet", length = 120)
    private String nomComplet;

    /** Format E.164 : +221XXXXXXXXX. */
    @Column(name = "livraison_telephone", length = 13)
    private String telephone;

    @Column(name = "livraison_ville", length = 80)
    private String ville;

    @Column(name = "livraison_quartier", length = 80)
    private String quartier;

    @Column(name = "livraison_adresse_ligne", length = 255)
    private String adresseLigne;

    @Column(name = "livraison_point_de_repere", length = 255)
    private String pointDeRepere;

    public AdresseLivraison(String nomComplet, String telephone, String ville, String quartier,
                            String adresseLigne, String pointDeRepere) {
        this.nomComplet = nomComplet;
        this.telephone = TelephoneSenegal.normaliserOuEchouer(telephone);
        this.ville = ville;
        this.quartier = quartier;
        this.adresseLigne = adresseLigne;
        this.pointDeRepere = pointDeRepere;
    }

    public static AdresseLivraison copieDe(Adresse adresse) {
        return new AdresseLivraison(adresse.getNomComplet(), adresse.getTelephone(), adresse.getVille(),
                adresse.getQuartier(), adresse.getAdresseLigne(), adresse.getPointDeRepere());
    }
}
