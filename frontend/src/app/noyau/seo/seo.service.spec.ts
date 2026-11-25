import { DOCUMENT } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';

import { ParametresPublics } from '../api/modeles';
import { SeoService } from './seo.service';

const PARAMETRES: Partial<ParametresPublics> = {
  nomSite: 'Boutique',
  imagePartageUrl: 'https://res.cloudinary.com/b/image/upload/c_fill,w_1200,h_630/v1/partage.jpg',
  favicons: {
    taille32: 'https://cdn/f32.png',
    appleTouch180: 'https://cdn/f180.png',
    manifeste192: 'https://cdn/f192.png',
    manifeste512: 'https://cdn/f512.png',
  },
  verification: { google: 'code-google', meta: null },
};

/** Laisse httpResource émettre sa requête, puis applique la réponse et les effets. */
async function stabiliser(): Promise<void> {
  TestBed.tick();
  await TestBed.inject(ApplicationRef).whenStable();
}

function configurer() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  const seo = TestBed.inject(SeoService);
  const http = TestBed.inject(HttpTestingController);
  return { seo, http };
}

describe('SeoService', () => {
  it('compose le titre avec le nom du site reçu de l’API, et un nom par défaut en attendant', async () => {
    const { seo, http } = configurer();
    seo.definirPage({ titre: 'PC portables', description: 'Les PC portables.' });
    TestBed.tick();
    expect(TestBed.inject(Title).getTitle()).toBe('PC portables | Boutique');

    http.expectOne('/api/parametres').flush({ ...PARAMETRES, nomSite: 'Ordi Dakar' });
    await stabiliser();
    expect(TestBed.inject(Title).getTitle()).toBe('PC portables | Ordi Dakar');
    expect(TestBed.inject(Meta).getTag('name="description"')?.content).toBe('Les PC portables.');
    expect(TestBed.inject(Meta).getTag('name="robots"')?.content).toBe('index,follow');
  });

  it('place dans le head les favicons, l’image de partage et les codes de vérification fournis', async () => {
    const { http } = configurer();
    TestBed.tick();
    http.expectOne('/api/parametres').flush(PARAMETRES);
    await stabiliser();

    const head = TestBed.inject(DOCUMENT).head;
    expect(head.querySelector('link[rel="icon"][sizes="32x32"]')?.getAttribute('href')).toBe('https://cdn/f32.png');
    expect(head.querySelector('link[rel="apple-touch-icon"]')?.getAttribute('href')).toBe('https://cdn/f180.png');
    const meta = TestBed.inject(Meta);
    expect(meta.getTag('property="og:image"')?.content).toBe(PARAMETRES.imagePartageUrl);
    expect(meta.getTag('name="google-site-verification"')?.content).toBe('code-google');
    expect(meta.getTag('name="facebook-domain-verification"')).toBeNull();

    // Une seconde application ne crée pas de doublon
    TestBed.inject(SeoService).definirPage({ titre: 'Autre' });
    await stabiliser();
    expect(head.querySelectorAll('link[rel="icon"][sizes="32x32"]')).toHaveLength(1);
  });
});
