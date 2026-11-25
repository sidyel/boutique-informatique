import { IconeConfiance } from '../../../noyau/api/modeles';
import { NomIcone } from './icone';

/** Pictogramme de chaque élément de confiance (bandeau de l'accueil, réassurance de la fiche produit). */
export const ICONES_CONFIANCE: Record<IconeConfiance, NomIcone> = {
  LIVRAISON: 'livraison',
  GARANTIE: 'garantie',
  PAIEMENT: 'paiement',
  SAV: 'sav',
  RETOUR: 'retour',
  BOUTIQUE: 'boutique',
};
