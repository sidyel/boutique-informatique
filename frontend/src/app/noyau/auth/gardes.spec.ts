import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { gardeConnecte, gardeVisiteur } from './gardes';
import { SessionService } from './session.service';

/** Session factice : la restauration est terminée, connectée ou non. */
class SessionFactice {
  connecte = () => this.estConnecte;
  estConnecte = false;
  restaurer = () => Promise.resolve();
}

describe('gardes de route', () => {
  let session: SessionFactice;

  beforeEach(() => {
    session = new SessionFactice();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SessionService, useValue: session }],
    });
  });

  const etat = (url: string) => ({ url }) as RouterStateSnapshot;
  const route = {} as ActivatedRouteSnapshot;

  it('zone réservée sans session : renvoie vers la connexion en gardant la page demandée', async () => {
    const resultat = await TestBed.runInInjectionContext(() => gardeConnecte(route, etat('/compte/commandes?page=2')));
    expect(resultat).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultat as UrlTree)).toBe('/connexion?retour=%2Fcompte%2Fcommandes%3Fpage%3D2');
  });

  it('zone réservée avec session : laisse passer', async () => {
    session.estConnecte = true;
    expect(await TestBed.runInInjectionContext(() => gardeConnecte(route, etat('/compte')))).toBe(true);
  });

  it('connexion et inscription : un client déjà connecté est renvoyé vers son compte', async () => {
    session.estConnecte = true;
    const resultat = await TestBed.runInInjectionContext(() => gardeVisiteur(route, etat('/connexion')));
    expect(TestBed.inject(Router).serializeUrl(resultat as UrlTree)).toBe('/compte');
  });

  it('connexion et inscription : un visiteur passe', async () => {
    expect(await TestBed.runInInjectionContext(() => gardeVisiteur(route, etat('/connexion')))).toBe(true);
  });
});
