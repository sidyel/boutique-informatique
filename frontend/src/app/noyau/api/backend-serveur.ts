import { FetchBackend, HttpBackend, HttpEvent, HttpRequest } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
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
 * Seule l'URL change : aucun en-tête de la requête du navigateur (cookies, X-Forwarded-For) n'est relayé.
 */
@Injectable()
export class BackendServeur implements HttpBackend {
  private readonly transport = inject(FetchBackend);
  private readonly base = inject(URL_API).replace(/\/$/, '');

  handle(requete: HttpRequest<unknown>): Observable<HttpEvent<unknown>> {
    if (!this.base || !requete.url.startsWith('/api/')) {
      return this.transport.handle(requete);
    }
    return this.transport.handle(requete.clone({ url: this.base + requete.url }));
  }
}
