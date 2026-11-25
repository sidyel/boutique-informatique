import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { DeferBlockState, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Banniere, ProduitResume } from '../../noyau/api/modeles';
import { SuiviService } from '../../noyau/suivi/suivi.service';
import { CarteProduit } from '../../partage/composants/carte-produit/carte-produit';
import { Accueil } from './accueil';
import { tempsRestant } from './banniere-promo/banniere-promo';
import { Carrousel, DUREE_DIAPO_MS } from './carrousel/carrousel';

function produit(id: number, champs: Partial<ProduitResume> = {}): ProduitResume {
  return {
    id,
    slug: `produit-${id}`,
    nom: `Produit ${id}`,
    marque: { nom: 'Asus', slug: 'asus' },
    categorie: { nom: 'PC portables', slug: 'pc-portables' },
    image: { url: `/demo/images/pc-${id}.svg`, texteAlternatif: null },
    varianteId: 100 + id,
    skuParDefaut: `SKU-${id}`,
    prix: 450000,
    prixBarre: null,
    reduction: null,
    etat: 'NEUF',
    disponibilite: 'EN_STOCK',
    noteMoyenne: null,
    nombreAvis: 0,
    nombreVariantes: 1,
    caracteristiquesCles: null,
    ...champs,
  };
}

function banniere(id: number, champs: Partial<Banniere> = {}): Banniere {
  return {
    id,
    titre: `Offre ${id}`,
    sousTitre: null,
    texteBouton: null,
    lien: '/categorie/pc-portables',
    imageOrdinateurUrl: `/demo/images/banniere-${id}-ordinateur.svg`,
    imageMobileUrl: `/demo/images/banniere-${id}-mobile.svg`,
    texteAlternatif: null,
    dateFin: null,
    ...champs,
  };
}

function configurer() {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
}

describe('CarteProduit', () => {
  it('mène à la variante présentée, n’affiche que le « −x % » en promotion et 4 caractéristiques au plus', () => {
    configurer();
    const fixture = TestBed.createComponent(CarteProduit);
    fixture.componentRef.setInput(
      'produit',
      produit(7, {
        prix: 649000,
        prixBarre: 729000,
        reduction: 11,
        disponibilite: 'STOCK_FAIBLE',
        caracteristiquesCles: [
          { libelle: 'CPU', valeur: 'Ryzen 7 7735HS' },
          { libelle: 'RAM', valeur: '16 Go' },
          { libelle: 'SSD', valeur: '512 Go' },
          { libelle: 'GPU', valeur: 'RTX 4060' },
          { libelle: 'Écran', valeur: '15,6 pouces' },
        ],
      }),
    );
    fixture.componentRef.setInput('liste', { id: 'accueil-promotions', nom: 'Promotions' });
    fixture.componentRef.setInput('position', 2);
    const choisir = vi.spyOn(TestBed.inject(SuiviService), 'choisirProduit');
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
    const carte: HTMLElement = fixture.nativeElement;

    const lien = carte.querySelector<HTMLAnchorElement>('.carte__lien')!;
    expect(lien.getAttribute('href')).toBe('/produit/produit-7?variante=107');
    expect(carte.querySelectorAll('.carte__specs dt')).toHaveLength(4);
    expect(carte.querySelector('.prix__reduction')?.textContent).toContain('−10 %');
    expect(carte.textContent).not.toMatch(/Promo\b/);
    expect(carte.querySelector('bi-badge')).toBeNull(); // produit neuf : aucun badge d'état
    expect(carte.querySelector('.dispo--faible')?.textContent).toContain('Stock limité');

    lien.click();
    expect(choisir).toHaveBeenCalledWith({ id: 'accueil-promotions', nom: 'Promotions' }, expect.anything(), 2);
  });
});

describe('Carrousel', () => {
  afterEach(() => vi.useRealTimers());

  async function rendre(bannieres: Banniere[]) {
    // Seul setInterval est simulé : la stabilité de l'application repose sur les autres minuteries
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    configurer();
    const fixture = TestBed.createComponent(Carrousel);
    fixture.componentRef.setInput('bannieres', bannieres);
    fixture.detectChanges();
    await TestBed.inject(ApplicationRef).whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  const active = (element: HTMLElement) =>
    Array.from(element.querySelectorAll('.diapo')).findIndex((d) => d.classList.contains('diapo--active'));

  it('défile seul, s’arrête au bouton pause et reprend au bouton lecture', async () => {
    const { fixture, element } = await rendre([banniere(1), banniere(2), banniere(3)]);

    vi.advanceTimersByTime(DUREE_DIAPO_MS);
    fixture.detectChanges();
    expect(active(element)).toBe(1);

    const pause = element.querySelector<HTMLButtonElement>('.controles .controle')!;
    expect(pause.getAttribute('aria-label')).toBe('Mettre en pause le défilement automatique');
    pause.click();
    fixture.detectChanges();
    expect(pause.getAttribute('aria-label')).toBe('Reprendre le défilement automatique');
    vi.advanceTimersByTime(DUREE_DIAPO_MS * 3);
    fixture.detectChanges();
    expect(active(element)).toBe(1);
    expect(element.querySelector('.carrousel__diapos')?.getAttribute('aria-live')).toBe('polite');

    pause.click();
    vi.advanceTimersByTime(DUREE_DIAPO_MS);
    fixture.detectChanges();
    expect(active(element)).toBe(2);
  });

  it('s’arrête au survol de la souris et quand le focus est dans le carrousel', async () => {
    const { fixture, element } = await rendre([banniere(1), banniere(2)]);
    const survol = new Event('pointerenter') as PointerEvent;
    Object.defineProperty(survol, 'pointerType', { value: 'mouse' });

    element.dispatchEvent(survol);
    vi.advanceTimersByTime(DUREE_DIAPO_MS * 2);
    fixture.detectChanges();
    expect(active(element)).toBe(0);

    element.dispatchEvent(new Event('pointerleave'));
    element.dispatchEvent(new FocusEvent('focusin'));
    vi.advanceTimersByTime(DUREE_DIAPO_MS * 2);
    fixture.detectChanges();
    expect(active(element)).toBe(0);
  });

  it('se pilote au clavier par les points et les flèches, et ne charge une image qu’à l’approche', async () => {
    const { fixture, element } = await rendre([banniere(1), banniere(2), banniere(3)]);
    const images = () => element.querySelectorAll('.diapo img');

    // Seule la première image au chargement, en priorité haute
    expect(images()).toHaveLength(1);
    expect(images()[0].getAttribute('fetchpriority')).toBe('high');
    expect(element.querySelector('source')?.getAttribute('media')).toBe('(max-width: 47.99rem)');

    const points = element.querySelectorAll<HTMLButtonElement>('.point');
    expect(points[0].getAttribute('aria-current')).toBe('true');
    points[2].click();
    fixture.detectChanges();
    expect(active(element)).toBe(2);
    expect(points[2].getAttribute('aria-current')).toBe('true');
    expect(images()).toHaveLength(2); // la 3e affichée et la suivante (la 1re), pas la 2e
    // Diapositives cachées : hors de l'ordre de tabulation
    expect((element.querySelectorAll('.diapo')[0] as HTMLElement).inert).toBe(true);

    element
      .querySelector('.controles')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(active(element)).toBe(0);
    expect(images()).toHaveLength(3); // retour à la 1re : la 2e (suivante) se charge
  });

  it('respecte prefers-reduced-motion : aucun défilement automatique', async () => {
    const origine = globalThis.matchMedia;
    globalThis.matchMedia = ((requete: string) => ({ matches: requete.includes('reduce') })) as typeof matchMedia;
    try {
      const { fixture, element } = await rendre([banniere(1), banniere(2)]);
      vi.advanceTimersByTime(DUREE_DIAPO_MS * 3);
      fixture.detectChanges();
      expect(active(element)).toBe(0);
      expect(element.querySelector('.controle')?.getAttribute('aria-label')).toBe(
        'Reprendre le défilement automatique',
      );
    } finally {
      globalThis.matchMedia = origine;
    }
  });
});

describe('tempsRestant', () => {
  it('affiche jours, heures et minutes, et rien une fois l’échéance passée', () => {
    const maintenant = Date.parse('2026-11-25T10:00:00Z');
    expect(tempsRestant(Date.parse('2026-11-27T15:12:00Z'), maintenant)).toBe('2 j 05 h 12 min');
    expect(tempsRestant(Date.parse('2026-11-25T10:45:30Z'), maintenant)).toBe('00 h 46 min');
    expect(tempsRestant(maintenant, maintenant)).toBeNull();
  });
});

describe('Accueil', () => {
  async function rendre(reponses: { accueil: object; carrousel?: Banniere[]; promo?: Banniere[] }) {
    TestBed.configureTestingModule({
      imports: [Accueil],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    await TestBed.compileComponents();
    const fixture = TestBed.createComponent(Accueil);
    fixture.detectChanges();
    TestBed.tick();
    const http = TestBed.inject(HttpTestingController);
    const accueil = http.expectOne('/api/accueil');
    if (reponses.accueil instanceof Error) {
      accueil.flush('erreur', { status: 500, statusText: 'Erreur' });
    } else {
      accueil.flush(reponses.accueil);
    }
    http.expectOne('/api/bannieres?emplacement=ACCUEIL_CARROUSEL').flush(reponses.carrousel ?? []);
    http.expectOne('/api/bannieres?emplacement=ACCUEIL_PROMO').flush(reponses.promo ?? []);
    http.match(() => true).forEach((r) => r.flush([]));
    await TestBed.inject(ApplicationRef).whenStable();
    fixture.detectChanges();
    for (const bloc of await fixture.getDeferBlocks()) {
      await bloc.render(DeferBlockState.Complete);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('affiche les sections avec les titres du serveur, sans section vide', async () => {
    const page = await rendre({
      accueil: {
        sections: [
          { code: 'nouveautes', titre: 'Nouveautés', produits: [produit(1), produit(2)] },
          { code: 'selection', titre: 'Sélection du moment', produits: [produit(3)] },
          { code: 'promotions', titre: 'Promotions', produits: [] },
        ],
      },
      promo: [banniere(9, { titre: 'Rentrée', dateFin: null })],
    });

    const titres = Array.from(page.querySelectorAll('.rangee__titre')).map((t) => t.textContent?.trim());
    expect(titres).toEqual(['Nouveautés', 'Sélection du moment']);
    expect(page.textContent).not.toContain('Meilleures ventes');
    // Bannière promo après la première section, sans compte à rebours faute de date de fin
    expect(page.querySelector('bi-banniere-promo')).not.toBeNull();
    expect(page.querySelector('.promo__echeance')).toBeNull();
    expect(page.querySelector('bi-carrousel')).toBeNull(); // aucune bannière de carrousel
  });

  it('masque les sections en cas d’erreur, sans laisser de squelette', async () => {
    const page = await rendre({ accueil: new Error() });

    expect(page.querySelector('bi-rangee-produits')).toBeNull();
    expect(page.querySelector('.rangee-squelette')).toBeNull();
    expect(page.querySelector('h1')?.textContent).toContain('matériel informatique au Sénégal');
  });
});
