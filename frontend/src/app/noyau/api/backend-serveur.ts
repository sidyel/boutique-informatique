import { FetchBackend, HttpBackend, HttpEvent, HttpRequest } from '@angular/common/http';
import { Injectable, REQUEST, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { URL_API } from './url-api';

/**
 * Transport HTTP du rendu serveur : les appels « /api/… » partent vers le backend (réseau Docker).
 *
 * La réécriture se fait ici, au dernier maillon, et non dans un intercepteur : l'intercepteur du
 * transfer cache d'Angular passe après les nôtres et range les réponses sous l'URL qu'il voit. Avec une
 * URL relative des deux côtés, le navigateur retrouve les réponses du SSR et ne rappelle pas l'API
 * (aucun double chargement). `HTTP_TRANSFER_CACHE_ORIGIN_MAP` ne convient pas : il ne sait pas
 * remplacer une origine par une URL relative.
 *
 * Attention : `provideServerRendering` ajoute un intercepteur racine qui rend les URL relatives absolues
 * avec l'origine de la requête reçue (`/api/x` → `http://hote-du-ssr/api/x`). Ces URL-là sont aussi
 * réécrites : sinon le serveur SSR s'appellerait lui-même, et chaque appel rendrait à nouveau l'application
 * (boucle sans fin constatée en phase 3a).
 *
 * Seule l'URL change : aucun en-tête de la requête du navigateur (cookies, X-Forwarded-For) n'est relayé.
 */
@Injectable()
export class BackendServeur implements HttpBackend {
  private readonly transport = inject(FetchBackend);
  private readonly base = inject(URL_API).replace(/\/$/, '');
  /** Origine de la requête rendue (`null` hors rendu d'une requête, au prérendu par exemple). */
  private readonly origine = originePage(inject(REQUEST, { optional: true })?.url);

  handle(requete: HttpRequest<unknown>): Observable<HttpEvent<unknown>> {
    const chemin = this.cheminApi(requete.url);
    if (!this.base || chemin === null) {
      return this.transport.handle(requete);
    }
    return this.transport.handle(requete.clone({ url: this.base + chemin }));
  }

  /** `/api/…` (avec la requête) si l'URL vise l'API du site, relative ou rendue absolue ; sinon `null`. */
  private cheminApi(url: string): string | null {
    if (url.startsWith('/api/')) {
      return url;
    }
    if (this.origine && url.startsWith(this.origine + '/api/')) {
      return url.slice(this.origine.length);
    }
    return null;
  }
}

function originePage(url: string | undefined): string | null {
  if (!url) {
    return null;
  }
  try {
    return new URL(url).origin;
  } catch {
    return null;
  }
}
