import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { retourSur } from './erreurs-api';
import { intercepteurAuth } from './intercepteur-auth';
import { Profil, SessionService } from './session.service';

const PROFIL: Profil = {
  id: 7,
  email: 'awa@exemple.sn',
  prenom: 'Awa',
  nom: 'Diop',
  telephone: null,
  role: 'CLIENT',
  emailVerifie: false,
  consentementMarketing: false, dateConsentement: null, sourceConsentement: null,
};
const reponse = (jeton: string, expireDans = 900) => ({ jetonAcces: jeton, typeJeton: 'Bearer', expireDans, utilisateur: PROFIL });

describe('SessionService et intercepteur', () => {
  let session: SessionService;
  let http: HttpClient;
  let controleur: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([intercepteurAuth])), provideHttpClientTesting()],
    });
    session = TestBed.inject(SessionService);
    http = TestBed.inject(HttpClient);
    controleur = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controleur.verify());

  async function connecter(jeton = 'jeton-1', expireDans = 900): Promise<void> {
    const promesse = session.connecter('awa@exemple.sn', 'un-mot-de-passe');
    controleur.expectOne('/api/auth/connexion').flush(reponse(jeton, expireDans));
    await promesse;
  }

  it('garde le jeton d’accès en mémoire seulement', async () => {
    await connecter('secret-en-memoire');
    expect(session.connecte()).toBe(true);
    expect(session.jetonAcces()).toBe('secret-en-memoire');
    const stockage = JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage });
    expect(stockage).not.toContain('secret-en-memoire');
  });

  it('un seul rafraîchissement à la fois, même demandé deux fois', async () => {
    await connecter('ancien', 10); // proche de l'expiration
    const a = session.rafraichir();
    const b = session.rafraichir();
    controleur.expectOne('/api/auth/rafraichir').flush(reponse('nouveau'));
    expect(await Promise.all([a, b])).toEqual([true, true]);
    expect(session.jetonAcces()).toBe('nouveau');
  });

  it('un rafraîchissement refusé déconnecte et efface l’indice de session', async () => {
    await connecter();
    const p = session.rafraichir(true);
    controleur.expectOne('/api/auth/rafraichir').flush({ code: 'SESSION_INVALIDE' }, { status: 401, statusText: 'Unauthorized' });
    expect(await p).toBe(false);
    expect(session.connecte()).toBe(false);
    expect(session.jetonAcces()).toBeNull();
    expect(localStorage.getItem('session_active')).toBeNull();
  });

  it('joint le jeton aux appels de l’API, sauf à l’authentification', async () => {
    await connecter('jeton-abc');
    const profil = firstValueFrom(http.get('/api/compte/profil'));
    const requete = controleur.expectOne('/api/compte/profil');
    expect(requete.request.headers.get('Authorization')).toBe('Bearer jeton-abc');
    requete.flush(PROFIL);
    await profil;

    const auth = firstValueFrom(http.post('/api/auth/deconnexion', {}));
    const deconnexion = controleur.expectOne('/api/auth/deconnexion');
    expect(deconnexion.request.headers.has('Authorization')).toBe(false);
    deconnexion.flush(null);
    await auth;
  });

  it('visiteur sans session : aucun en-tête Authorization', async () => {
    const p = firstValueFrom(http.get('/api/panier'));
    const requete = controleur.expectOne('/api/panier');
    expect(requete.request.headers.has('Authorization')).toBe(false);
    requete.flush({});
    await p;
  });

  it('401 : un rafraîchissement puis un seul nouvel essai', async () => {
    await connecter('perime');
    const p = firstValueFrom(http.get('/api/compte/profil'));
    controleur.expectOne('/api/compte/profil').flush({}, { status: 401, statusText: 'Unauthorized' });
    await Promise.resolve();
    controleur.expectOne('/api/auth/rafraichir').flush(reponse('frais'));
    await new Promise((r) => setTimeout(r));
    const rejeu = controleur.expectOne('/api/compte/profil');
    expect(rejeu.request.headers.get('Authorization')).toBe('Bearer frais');
    rejeu.flush(PROFIL);
    expect(await p).toEqual(PROFIL);
  });

  it('401 persistant : l’erreur est renvoyée, pas de boucle', async () => {
    await connecter('perime');
    const p = firstValueFrom(http.get('/api/compte/profil')).catch((e: { status: number }) => e.status);
    controleur.expectOne('/api/compte/profil').flush({}, { status: 401, statusText: 'Unauthorized' });
    await Promise.resolve();
    controleur.expectOne('/api/auth/rafraichir').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(await p).toBe(401);
    expect(session.connecte()).toBe(false);
  });

  it('la déconnexion oublie la session même si le serveur est injoignable', async () => {
    await connecter();
    const p = session.deconnecter();
    controleur.expectOne('/api/auth/deconnexion').error(new ProgressEvent('error'));
    await p;
    expect(session.connecte()).toBe(false);
    expect(localStorage.getItem('session_active')).toBeNull();
  });
});

describe('retourSur', () => {
  it('n’accepte que des chemins internes', () => {
    expect(retourSur('/commande')).toBe('/commande');
    expect(retourSur('/compte/commandes?page=2')).toBe('/compte/commandes?page=2');
    expect(retourSur('//autre-site.com')).toBe('/compte');
    expect(retourSur('https://autre-site.com')).toBe('/compte');
    expect(retourSur('/\\autre-site.com')).toBe('/compte');
    expect(retourSur(null)).toBe('/compte');
  });
});
