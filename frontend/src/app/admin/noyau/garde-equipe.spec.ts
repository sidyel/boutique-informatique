import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { SessionService } from '../../noyau/auth/session.service';
import { gardeEquipe, gardeRoles } from './garde-equipe';

class SessionFactice {
  role: string | null = null;
  utilisateur = () => (this.role ? { role: this.role } : null);
  restaurer = () => Promise.resolve();
}

describe('gardes de l’administration', () => {
  let session: SessionFactice;
  const route = {} as ActivatedRouteSnapshot;
  const etat = { url: '/admin/journal' } as RouterStateSnapshot;

  beforeEach(() => {
    session = new SessionFactice();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SessionService, useValue: session }],
    });
  });

  it('un visiteur est renvoyé vers la connexion de l’équipe, avec la page demandée', async () => {
    const r = await TestBed.runInInjectionContext(() => gardeEquipe(route, etat));
    expect(r).toBeInstanceOf(UrlTree);
    expect((r as UrlTree).toString()).toBe('/admin/connexion?retour=%2Fadmin%2Fjournal');
  });

  it('un client connecté n’entre pas dans l’administration', async () => {
    session.role = 'CLIENT';
    expect(await TestBed.runInInjectionContext(() => gardeEquipe(route, etat))).toBeInstanceOf(UrlTree);
  });

  it('un membre de l’équipe entre', async () => {
    session.role = 'SERVICE_CLIENT';
    expect(await TestBed.runInInjectionContext(() => gardeEquipe(route, etat))).toBe(true);
  });

  it('le journal est réservé au super-administrateur', async () => {
    session.role = 'ADMIN';
    const garde = gardeRoles('SUPER_ADMIN');
    expect(await TestBed.runInInjectionContext(() => garde(route, etat))).toBeInstanceOf(UrlTree);
    session.role = 'SUPER_ADMIN';
    expect(await TestBed.runInInjectionContext(() => garde(route, etat))).toBe(true);
  });
});
