import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, RESPONSE_INIT, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { ReponseDefinitive, reponseDefinitive } from './reponse-definitive';

const chemin = signal<string | null>(null);

@Component({ selector: 'bi-hote', template: '' })
class Hote {
  readonly definitive: ReponseDefinitive = reponseDefinitive(() => chemin());
}

describe('reponseDefinitive', () => {
  let reponse: ResponseInit;

  function creer() {
    reponse = { status: 200 };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', component: Hote }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RESPONSE_INIT, useValue: reponse },
      ],
    });
    const fixture = TestBed.createComponent(Hote);
    return { hote: fixture.componentInstance, http: TestBed.inject(HttpTestingController) };
  }

  beforeEach(() => chemin.set(null));

  it("ne demande rien tant que l'API n'a pas répondu 404", () => {
    const { hote, http } = creer();
    TestBed.tick();
    http.expectNone(() => true);
    expect(hote.definitive.introuvable()).toBe(false);
    expect(reponse.status).toBe(200);
  });

  it('301 : pose le statut et navigue vers la nouvelle adresse en remplaçant l’historique', () => {
    const { http } = creer();
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    chemin.set('/produit/ancien');
    TestBed.tick();
    http.expectOne('/api/redirections?chemin=%2Fproduit%2Fancien')
      .flush({ destination: '/produit/nouveau', statut: 301, retrait: null });
    TestBed.tick();

    expect(reponse.status).toBe(301);
    expect(navigation).toHaveBeenCalledOnce();
    expect(String(navigation.mock.calls[0][0])).toBe('/produit/nouveau');
    expect(navigation.mock.calls[0][1]).toEqual({ replaceUrl: true });
  });

  it('410 : pose le statut et expose le retrait', () => {
    const { hote, http } = creer();
    chemin.set('/produit/eee-pc');
    TestBed.tick();
    const retrait = { nom: 'Eee PC', categorie: { nom: 'Ordinateurs', slug: 'ordinateurs' } };
    http.expectOne('/api/redirections?chemin=%2Fproduit%2Feee-pc')
      .flush({ destination: null, statut: 410, retrait });
    TestBed.tick();

    expect(reponse.status).toBe(410);
    expect(hote.definitive.retrait()).toEqual(retrait);
    expect(hote.definitive.introuvable()).toBe(false);
  });

  it('404 du backend : vraie page introuvable', () => {
    const { hote, http } = creer();
    chemin.set('/produit/inconnu');
    TestBed.tick();
    http.expectOne('/api/redirections?chemin=%2Fproduit%2Finconnu')
      .flush(null, { status: 404, statusText: 'Not Found' });
    TestBed.tick();

    expect(hote.definitive.introuvable()).toBe(true);
    expect(hote.definitive.retrait()).toBeNull();
    expect(reponse.status).toBe(200); // posé à 404 par la page introuvable elle-même
  });
});
