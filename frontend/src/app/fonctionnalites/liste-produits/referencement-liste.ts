import { CodeTri, Criteres, nombreFiltres, versParametres } from './criteres';
import { urlAvecParametres } from './source-liste';

/**
 * Référencement d'une liste (catégorie, marque), ADR 0007 :
 * - sans filtre ni tri choisi : indexable, canonique vers elle-même (`?page=2` gardé) ;
 * - filtrée ou triée : `noindex,follow`, canonique vers elle-même avec des paramètres normalisés (ordre fixe,
 *   valeurs triées, défauts et paramètres inconnus retirés), jamais vers la page sans filtre (Google
 *   déconseille d'associer `noindex` et une canonique qui pointe ailleurs).
 */
export function referencementListe(
  chemin: string,
  criteres: Criteres,
  triParDefaut: CodeTri = 'recommande',
): { canonique: string; indexable: boolean } {
  const params = versParametres(criteres, triParDefaut);
  const indexable = nombreFiltres(criteres) === 0 && !('tri' in params);
  return { canonique: urlAvecParametres(chemin, params), indexable };
}
