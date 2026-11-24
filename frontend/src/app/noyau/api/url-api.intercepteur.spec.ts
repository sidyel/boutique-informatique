import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { URL_API } from './url-api';
import { urlApiIntercepteur } from './url-api.intercepteur';

function configurer(base: string): { http: HttpClient; controleur: HttpTestingController } {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([urlApiIntercepteur])),
      provideHttpClientTesting(),
      { provide: URL_API, useValue: base },
    ],
  });
  return { http: TestBed.inject(HttpClient), controleur: TestBed.inject(HttpTestingController) };
}

describe('urlApiIntercepteur', () => {
  it("laisse l'URL relative dans le navigateur", () => {
    const { http, controleur } = configurer('');
    http.get('/api/produits').subscribe();
    controleur.expectOne('/api/produits').flush([]);
  });

  it("préfixe l'URL du backend pendant le rendu serveur", () => {
    const { http, controleur } = configurer('http://backend:8080/');
    http.get('/api/produits').subscribe();
    controleur.expectOne('http://backend:8080/api/produits').flush([]);
  });

  it('ne touche pas aux URL externes', () => {
    const { http, controleur } = configurer('http://backend:8080');
    http.get('https://res.cloudinary.com/demo/image.json').subscribe();
    controleur.expectOne('https://res.cloudinary.com/demo/image.json').flush({});
  });
});
