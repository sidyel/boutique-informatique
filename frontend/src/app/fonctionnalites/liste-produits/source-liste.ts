import { HttpParams } from '@angular/common/http';
import { Params } from '@angular/router';
import { Observable } from 'rxjs';

import { Filtres } from '../../noyau/api/modeles';
import { OrigineWhatsApp } from '../../noyau/suivi/suivi.service';
import { CodeTri, Criteres, OptionTri } from './criteres';

/** Ce qui distingue une page de liste (catégorie, marque, recherche) pour la vue commune. */
export interface SourceListe {
  triParDefaut: CodeTri;
  tris: readonly OptionTri[];
  /** Les catégories sont-elles un filtre (marque, recherche) ou des liens vers des sous-rayons (catégorie) ? */
  categoriesEnFiltre: boolean;
  /** Facettes pour des critères en cours de choix (feuille mobile : « Voir 12 produits » en direct). */
  filtresPour(criteres: Criteres): Observable<Filtres>;
  /** Message WhatsApp prérempli quand la liste est vide. */
  messageWhatsApp(criteres: Criteres): string;
  origineWhatsApp: OrigineWhatsApp;
}

/** `/api/produits` + paramètres canoniques → URL complète, clé stable pour la mémoire et le transfer cache. */
export function urlAvecParametres(chemin: string, params: Params): string {
  const requete = new HttpParams({ fromObject: params as Record<string, string | string[]> }).toString();
  return requete ? `${chemin}?${requete}` : chemin;
}
