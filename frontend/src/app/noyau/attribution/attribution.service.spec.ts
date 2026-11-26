import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AttributionService, construireTouche } from './attribution.service';

const SITE = 'boutique.sn';

describe('construireTouche', () => {
  it('lit les UTM et garde la page d’arrivée sans identifiant de régie', () => {
    const touche = construireTouche(
      'https://boutique.sn/categorie/pc-portables?utm_source=facebook&utm_medium=cpc&utm_campaign=soldes&gclid=ABC&fbclid=DEF&page=2',
      'https://l.facebook.com/',
      SITE,
    );

    expect(touche).toMatchObject({ source: 'facebook', medium: 'cpc', campagne: 'soldes', direct: false });
    expect(touche.page).toBe('/categorie/pc-portables?utm_source=facebook&utm_medium=cpc&utm_campaign=soldes&page=2');
    expect(JSON.stringify(touche)).not.toMatch(/gclid|fbclid|ABC|DEF/);
  });

  it('classe un moteur de recherche en « organic » et un autre site en « referral »', () => {
    expect(construireTouche('https://boutique.sn/', 'https://www.google.com/', SITE)).toMatchObject({
      source: 'www.google.com',
      medium: 'organic',
      direct: false,
    });
    expect(construireTouche('https://boutique.sn/', 'https://blog.exemple.sn/avis', SITE)).toMatchObject({
      source: 'blog.exemple.sn',
      medium: 'referral',
    });
  });

  it('une visite directe, ou venue du site lui-même, est « direct »', () => {
    expect(construireTouche('https://boutique.sn/', '', SITE).direct).toBe(true);
    expect(construireTouche('https://boutique.sn/panier', 'https://boutique.sn/', SITE).direct).toBe(true);
  });

  it('nettoie les caractères de contrôle et tronque les valeurs', () => {
    const touche = construireTouche(
      `https://boutique.sn/?utm_source=a%00b%0Ac&utm_campaign=${'x'.repeat(400)}`,
      '',
      SITE,
    );

    expect(touche.source).toBe('a b c');
    expect(touche.campagne).toHaveLength(100);
  });
});

describe('AttributionService', () => {
  function creer(plateforme: 'browser' | 'server' = 'browser') {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: PLATFORM_ID, useValue: plateforme }] });
    return TestBed.inject(AttributionService);
  }

  function viderCookie(): void {
    document.cookie = 'bi_attribution=; Max-Age=0; Path=/';
  }

  beforeEach(viderCookie);
  afterEach(viderCookie);

  it('ne touche à aucun cookie pendant le rendu serveur', () => {
    const service = creer('server');
    service.capturer();
    expect(document.cookie).not.toContain('bi_attribution');
    expect(service.lire()).toBeNull();
  });

  it('garde le premier contact et le dernier contact non direct', () => {
    const service = creer();
    // Aucun UTM dans l'URL des tests (about:blank / localhost sans paramètre) : une visite directe
    service.capturer();
    expect(service.lire()).toMatchObject({ utmSource: '(direct)', premiereSource: '(direct)' });

    // Un contact publicitaire remplace le dernier contact, jamais le premier
    document.cookie = `bi_attribution=${encodeURIComponent(
      JSON.stringify({
        premier: { source: 'google', medium: 'organic', campagne: null, contenu: null, terme: null, page: '/', referent: null, direct: false },
        dernier: { source: 'facebook', medium: 'cpc', campagne: 'soldes', contenu: null, terme: null, page: '/promo', referent: null, direct: false },
      }),
    )}; Path=/`;
    // Une visite directe ne remplace rien
    service.capturer();
    expect(service.lire()).toMatchObject({
      utmSource: 'facebook',
      utmCampagne: 'soldes',
      pageArrivee: '/promo',
      premiereSource: 'google',
      premierMedium: 'organic',
    });
  });

  it('ignore un cookie illisible', () => {
    document.cookie = 'bi_attribution=%7Bpas-du-json; Path=/';
    expect(creer().lire()).toBeNull();
  });
});
