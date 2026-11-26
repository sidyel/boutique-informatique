import { Panier } from '../api/modeles';
import { ArticleSuivi } from '../suivi/suivi.service';

/** Lignes commandables du panier au format des événements de suivi (SKU, prix recalculés par le serveur). */
export function articlesSuivis(panier: Panier): ArticleSuivi[] {
  return panier.lignes
    .filter((ligne) => ligne.probleme !== 'INDISPONIBLE')
    .map((ligne) => ({
      sku: ligne.sku,
      nom: ligne.nomProduit,
      marque: ligne.marque,
      categorie: ligne.categorie,
      variante: ligne.libelleVariante,
      prix: ligne.prixUnitaire,
      quantite: ligne.quantite,
    }));
}

/** Valeur d'un événement de panier : sous-total moins la remise, hors livraison. */
export function valeurSuivie(panier: Panier): number {
  return Math.max(0, panier.sousTotal - panier.remise);
}
