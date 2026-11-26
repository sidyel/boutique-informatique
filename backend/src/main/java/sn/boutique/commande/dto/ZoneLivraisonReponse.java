package sn.boutique.commande.dto;

import java.util.List;

import sn.boutique.commande.modele.ZoneLivraison;

/**
 * Zone de livraison publiée : « Dakar Plateau et centre », 2 000 FCFA, 1 jour.
 *
 * @param tarif      frais de livraison en FCFA
 * @param delaiJours délai indicatif en jours
 */
public record ZoneLivraisonReponse(long id, String nom, List<String> villesQuartiers, long tarif, int delaiJours) {

    public static ZoneLivraisonReponse depuis(ZoneLivraison zone) {
        return new ZoneLivraisonReponse(zone.getId(), zone.getNom(), List.copyOf(zone.getVillesQuartiers()), zone.getTarif(),
                zone.getDelaiJours());
    }
}
