package sn.boutique.compte.dto;

import sn.boutique.compte.modele.Adresse;

public record AdresseReponse(Long id, String libelle, String nomComplet, String telephone, String ville,
                             String quartier, String adresseLigne, String pointDeRepere, boolean parDefaut) {

    public static AdresseReponse de(Adresse a) {
        return new AdresseReponse(a.getId(), a.getLibelle(), a.getNomComplet(), a.getTelephone(), a.getVille(),
                a.getQuartier(), a.getAdresseLigne(), a.getPointDeRepere(), a.isParDefaut());
    }
}
