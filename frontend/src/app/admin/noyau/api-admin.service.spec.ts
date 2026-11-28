import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { ApiAdminService } from './api-admin.service';

const refus = () =>
  new HttpErrorResponse({ status: 403, error: { code: 'REAUTHENTIFICATION_REQUISE' } });

describe('ApiAdminService — réauthentification', () => {
  let api: ApiAdminService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiAdminService);
    http = TestBed.inject(HttpTestingController);
  });

  it('ouvre la fenêtre de code puis rejoue l’action une seule fois', async () => {
    let appels = 0;
    const resultat = api.appeler(async () => {
      appels++;
      if (appels === 1) throw refus();
      return 'fait';
    });
    await Promise.resolve();
    await Promise.resolve();
    expect(api.reauthentificationOuverte()).toBe(true);

    const validation = api.validerCode('123456');
    http.expectOne('/api/admin/reauthentification').flush({});
    await validation;
    expect(await resultat).toBe('fait');
    expect(appels).toBe(2);
    expect(api.reauthentificationOuverte()).toBe(false);
  });

  it('annuler la fenêtre laisse remonter le refus d’origine', async () => {
    const resultat = api.appeler(async () => {
      throw refus();
    });
    await Promise.resolve();
    await Promise.resolve();
    api.annulerCode();
    await expect(resultat).rejects.toBeInstanceOf(HttpErrorResponse);
  });

  it('une autre erreur 403 n’ouvre pas la fenêtre', async () => {
    await expect(
      api.appeler(async () => {
        throw new HttpErrorResponse({ status: 403, error: { code: 'ACCES_REFUSE' } });
      }),
    ).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(api.reauthentificationOuverte()).toBe(false);
  });
});
