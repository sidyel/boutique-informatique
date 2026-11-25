import { Pipe, PipeTransform } from '@angular/core';

/**
 * Espace insécable normale (U+00A0) entre les milliers. L'espace fine (U+202F), plus typographique,
 * était presque invisible à l'écran : on lisait « 649000 ».
 */
const SEPARATEUR_MILLIERS = ' ';
/** Espace insécable : « FCFA » ne passe jamais seul à la ligne. */
const ESPACE_DEVISE = ' ';

/**
 * Formate un montant en francs CFA : 450000 → « 450 000 FCFA ».
 * Les montants sont des entiers (le franc CFA n'a pas de décimales).
 *
 * Le formatage est fait à la main plutôt qu'avec Intl : le résultat est identique
 * côté serveur (Node) et dans tous les navigateurs, donc aucune différence à l'hydratation.
 */
export function formaterPrixFcfa(montant: number | null | undefined): string {
  if (montant === null || montant === undefined || !Number.isFinite(montant)) {
    return '';
  }
  const entier = Math.round(montant);
  const chiffres = String(Math.abs(entier)).replace(/\B(?=(\d{3})+(?!\d))/g, SEPARATEUR_MILLIERS);
  return `${entier < 0 ? '−' : ''}${chiffres}${ESPACE_DEVISE}FCFA`;
}

@Pipe({ name: 'prixFcfa' })
export class PrixFcfaPipe implements PipeTransform {
  transform(montant: number | null | undefined): string {
    return formaterPrixFcfa(montant);
  }
}
