import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ParametresPublics } from '../../../noyau/api/modeles';
import { ParametresSiteService } from '../../../noyau/donnees/parametres-site.service';
import { PanierService } from '../../../noyau/panier/panier.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { SuiviService } from '../../../noyau/suivi/suivi.service';
import { PagePanier } from './page-panier';

function monter(parametres: Partial<ParametresPublics> | undefined) {
  const params = signal(parametres as ParametresPublics | undefined);
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      {
        provide: ParametresSiteService,
        useValue: {
          parametres: params,
          modesPaiement: () => params()?.modesPaiement ?? { mobileMoney: false, carte: false, livraison: true },
        },
      },
      {
        provide: PanierService,
        useValue: {
          panier: signal(null),
          nombreArticles: signal(0),
          erreur: signal(null),
          enCours: signal(false),
          zoneId: signal(null),
          charger: () => Promise.resolve(),
        },
      },
      { provide: SeoService, useValue: { definirPage: () => undefined } },
      { provide: SuiviService, useValue: { voirPanier: () => undefined } },
    ],
  });
  const fixture = TestBed.createComponent(PagePanier);
  fixture.detectChanges();
  return { fixture, params, el: fixture.nativeElement as HTMLElement };
}

const AUCUN = { mobileMoney: false, carte: false, livraison: false };
const LIVRAISON = { mobileMoney: false, carte: false, livraison: true };

describe('PagePanier : phrase d’information', () => {
  it('affiche le délai et les moyens de paiement des paramètres, sans rien écrire en dur', () => {
    const { el } = monter({
      delaiLivraison: 'Livré à Dakar en 48 h',
      modesPaiement: { mobileMoney: true, carte: false, livraison: false },
    });
    const texte = el.querySelector('.page-panier__info')?.textContent ?? '';
    expect(texte).toContain('Livré à Dakar en 48 h.');
    expect(texte).toContain('Paiement par Wave, Orange Money, Free Money.');
    expect(texte).not.toContain('à la livraison');
  });

  it('disparaît quand rien n’est configuré ni actif', () => {
    const { el } = monter({ delaiLivraison: null, modesPaiement: AUCUN });
    expect(el.querySelector('.page-panier__info')).toBeNull();
  });

  it('disparaît tant que les paramètres ne sont pas connus', () => {
    const { el } = monter(undefined);
    expect(el.querySelector('.page-panier__info')).toBeNull();
  });

  it('se met à jour quand les paramètres changent', () => {
    const { fixture, params, el } = monter({ delaiLivraison: null, modesPaiement: LIVRAISON });
    expect(el.querySelector('.page-panier__info')?.textContent).toContain('Paiement à la livraison');
    params.set({ delaiLivraison: 'Livré en 24 h', modesPaiement: AUCUN } as ParametresPublics);
    fixture.detectChanges();
    expect(el.querySelector('.page-panier__info')?.textContent?.trim()).toBe('Livré en 24 h.');
  });
});
