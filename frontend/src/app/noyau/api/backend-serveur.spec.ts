import {
  FetchBackend,
  HttpBackend,
  HttpClient,
  HttpEvent,
  HttpRequest,
  HttpResponse,
  provideHttpClient,
  withFetch,
  withInterceptors,
} from '@angular/common/http';
import { REQUEST } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';

import { BackendServeur } from './backend-serveur';
import { URL_API } from './url-api';

/** Faux transport : enregistre les requêtes qui partent réellement sur le réseau. */
class FauxFetch {
  readonly envoyees: HttpRequest<unknown>[] = [];

  handle(requete: HttpRequest<unknown>): Observable<HttpEvent<unknown>> {
    this.envoyees.push(requete);
    return of(new HttpResponse({ status: 200, body: {} }));
  }
}

function configurer(base: string, urlPage?: string) {
  const vues: string[] = [];
  TestBed.configureTestingModule({
    providers: [
      // Un intercepteur témoin, au même rang que celui du transfer cache d'Angular
      provideHttpClient(
        withFetch(),
        withInterceptors([
          (requete, suivant) => {
            vues.push(requete.url);
            return suivant(requete);
          },
        ]),
      ),
      { provide: URL_API, useValue: base },
      ...(urlPage ? [{ provide: REQUEST, useValue: new Request(urlPage) }] : []),
      { provide: FetchBackend, useClass: FauxFetch },
      { provide: HttpBackend, useClass: BackendServeur },
    ],
  });
  return {
    http: TestBed.inject(HttpClient),
    transport: TestBed.inject(FetchBackend) as unknown as FauxFetch,
    vues,
  };
}

describe('BackendServeur', () => {
  it("envoie les appels /api au backend, mais les intercepteurs (transfer cache) voient l'URL relative", () => {
    const { http, transport, vues } = configurer('http://backend:8080/');
    http.get('/api/produits?page=2').subscribe();

    expect(vues).toEqual(['/api/produits?page=2']);
    expect(transport.envoyees.map((r) => r.urlWithParams)).toEqual([
      'http://backend:8080/api/produits?page=2',
    ]);
  });

  it("ne relaie aucun en-tête : seule l'URL change", () => {
    const { http, transport } = configurer('http://backend:8080');
    http.get('/api/accueil').subscribe();
    expect(transport.envoyees[0].headers.keys()).toEqual([]);
  });

  it('ne touche ni aux URL externes ni aux appels sans base (navigateur)', () => {
    const { http, transport } = configurer('http://backend:8080');
    http.get('https://res.cloudinary.com/demo/image.json').subscribe();
    expect(transport.envoyees[0].url).toBe('https://res.cloudinary.com/demo/image.json');

    TestBed.resetTestingModule();
    const navigateur = configurer('');
    navigateur.http.get('/api/accueil').subscribe();
    expect(navigateur.transport.envoyees[0].url).toBe('/api/accueil');
  });

  it('réécrit aussi l’URL rendue absolue avec l’origine de la page : le SSR ne s’appelle jamais lui-même', () => {
    const { http, transport } = configurer('http://backend:8080', 'http://boutique.sn/guide-style');
    http.get('http://boutique.sn/api/parametres').subscribe();
    http.get('http://autre-site.sn/api/parametres').subscribe();

    expect(transport.envoyees.map((r) => r.url)).toEqual([
      'http://backend:8080/api/parametres',
      'http://autre-site.sn/api/parametres',
    ]);
  });
});
