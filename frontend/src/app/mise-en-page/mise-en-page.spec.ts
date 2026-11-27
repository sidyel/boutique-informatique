import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef, Type } from '@angular/core';
import { ComponentFixture, DeferBlockState, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { CategorieNoeud, ParametresPublics } from '../noyau/api/modeles';
import { DELAI_FERMETURE_MS, DELAI_OUVERTURE_MS, MegaMenu } from './mega-menu/mega-menu';
import { MiseEnPagePublique } from './mise-en-page-publique';

const PARAMETRES: Partial<ParametresPublics> = {
  nomSite: 'Boutique',
  logoUrl: null,
  telephone: '+221338000000',
  whatsapp: '+221770000000',
  messageBarreSuperieure: 'Livraison à Dakar en 24 h',
  elementsConfiance: [],
  liensReseauxSociaux: { facebook: 'https://www.facebook.com/boutique' },
  verification: { google: null, meta: null },
};

const CATEGORIES: CategorieNoeud[] = [
  {
    nom: 'Ordinateurs',
    slug: 'ordinateurs',
    imageUrl: null,
    nombreProduits: 12,
    enfants: [
      { nom: 'PC portables', slug: 'pc-portables', imageUrl: null, nombreProduits: 8, enfants: [] },
      { nom: 'PC de bureau', slug: 'pc-de-bureau', imageUrl: null, nombreProduits: 0, enfants: [] },
    ],
  },
  { nom: 'Onduleurs', slug: 'onduleurs', imageUrl: null, nombreProduits: 3, enfants: [] },
];

async function rendre<T>(composant: Type<T>, parametres: Partial<ParametresPublics> = PARAMETRES) {
  TestBed.configureTestingModule({
    imports: [composant],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  await TestBed.compileComponents();
  const fixture = TestBed.createComponent(composant);
  fixture.detectChanges();
  // Blocs @defer (hydrate on viewport) : rendus d'emblée, comme au rendu serveur
  for (const bloc of await fixture.getDeferBlocks()) {
    await bloc.render(DeferBlockState.Complete);
  }
  const http = TestBed.inject(HttpTestingController);
  http.match('/api/parametres').forEach((r) => r.flush(parametres));
  http.match('/api/categories').forEach((r) => r.flush(CATEGORIES));
  http.match('/api/marques').forEach((r) =>
    r.flush([{ nom: 'Asus', slug: 'asus', logoUrl: null, nombreProduits: 5 }]),
  );
  await TestBed.inject(ApplicationRef).whenStable();
  fixture.detectChanges();
  return fixture as ComponentFixture<T>;
}

describe('MiseEnPagePublique', () => {
  it('a un lien d’évitement, la barre supérieure des paramètres et des liens directs tel: et wa.me', async () => {
    const fixture = await rendre(MiseEnPagePublique);
    const page: HTMLElement = fixture.nativeElement;

    expect(page.querySelector('a.lien-evitement')?.getAttribute('href')).toBe('#contenu');
    expect(page.querySelector('main#contenu')).not.toBeNull();
    expect(page.querySelector('.barre-superieure__message')?.textContent).toContain('Livraison à Dakar en 24 h');
    expect(page.querySelector('.barre-superieure a[href="tel:+221338000000"]')).not.toBeNull();
    expect(page.querySelector('.whatsapp')?.getAttribute('href')).toBe('https://wa.me/221770000000');
    expect(page.querySelector('[aria-label="Panier, 0 article"]')).not.toBeNull();
    // Logo texte provisoire tant qu'aucun logo n'est envoyé
    expect(page.querySelector('.logo__nom')?.textContent).toContain('Boutique');
    // Pied de page : catégories, paiements, réseaux
    expect(page.querySelector('.pied')?.textContent).toContain('Paiement à la livraison');
    expect(page.querySelector('.pied a[href="https://www.facebook.com/boutique"]')).not.toBeNull();
  });

  it('le pied de page n’annonce que les moyens de paiement actifs', async () => {
    const sans = (await rendre(MiseEnPagePublique)).nativeElement.querySelector('.pied')?.textContent ?? '';
    expect(sans).toContain('Paiement à la livraison');
    expect(sans).not.toMatch(/Wave|Orange Money|Free Money|Carte bancaire/);
    TestBed.resetTestingModule();

    const avec = (
      await rendre(MiseEnPagePublique, {
        ...PARAMETRES,
        modesPaiement: { mobileMoney: true, carte: false, livraison: true },
      })
    ).nativeElement.querySelector('.pied')?.textContent ?? '';
    expect(avec).toMatch(/Wave/);
    expect(avec).toContain('Orange Money');
    expect(avec).not.toContain('Carte bancaire');
  });

  it('n’écrit aucune promesse ni bouton WhatsApp quand les paramètres sont vides', async () => {
    const fixture = await rendre(MiseEnPagePublique, {
      ...PARAMETRES,
      whatsapp: null,
      messageBarreSuperieure: null,
      logoUrl: 'https://res.cloudinary.com/b/image/upload/fl_sanitize/v1/logo.svg',
    });
    const page: HTMLElement = fixture.nativeElement;

    expect(page.querySelector('.barre-superieure__message')).toBeNull();
    expect(page.querySelector('.whatsapp')).toBeNull();
    // Logo envoyé : affiché par <img src>, jamais incorporé
    expect(page.querySelector('img.logo__image')?.getAttribute('src')).toContain('logo.svg');
  });

  it('ouvre le tiroir en fenêtre modale, ferme avec Échap et rend le focus au bouton', async () => {
    const fixture = await rendre(MiseEnPagePublique);
    const page: HTMLElement = fixture.nativeElement;
    const bouton = page.querySelector<HTMLButtonElement>('.principal__menu')!;

    bouton.click();
    fixture.detectChanges();
    const tiroir = page.querySelector<HTMLElement>('[role="dialog"]')!;
    expect(tiroir.getAttribute('aria-modal')).toBe('true');
    expect(bouton.getAttribute('aria-expanded')).toBe('true');
    // Sous-catégorie vide masquée, catégorie sans enfant en lien direct
    expect(tiroir.textContent).not.toContain('PC de bureau');
    expect(tiroir.querySelector('a[href="/categorie/onduleurs"]')).not.toBeNull();

    tiroir.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(page.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(bouton);
  });
});

describe('MegaMenu', () => {
  afterEach(() => vi.useRealTimers());

  function survol(element: Element, type: string, pointerType: string) {
    const evenement = new Event(type, { bubbles: false }) as PointerEvent;
    Object.defineProperty(evenement, 'pointerType', { value: pointerType });
    element.dispatchEvent(evenement);
  }

  it('ne s’ouvre qu’après le délai d’intention et tolère un court écart de la souris', async () => {
    const fixture = await rendre(MegaMenu);
    vi.useFakeTimers();
    const menu: HTMLElement = fixture.nativeElement;
    const entree = menu.querySelector('[data-categorie="ordinateurs"]')!;
    const panneau = () => menu.querySelector<HTMLElement>('#panneau-ordinateurs')!;

    survol(entree, 'pointerenter', 'mouse');
    vi.advanceTimersByTime(DELAI_OUVERTURE_MS - 10);
    fixture.detectChanges();
    expect(panneau().hidden).toBe(true);
    vi.advanceTimersByTime(20);
    fixture.detectChanges();
    expect(panneau().hidden).toBe(false);

    // Sortie brève puis retour : reste ouvert
    survol(menu, 'pointerleave', 'mouse');
    vi.advanceTimersByTime(DELAI_FERMETURE_MS - 50);
    survol(menu, 'pointerenter', 'mouse');
    vi.advanceTimersByTime(DELAI_FERMETURE_MS);
    fixture.detectChanges();
    expect(panneau().hidden).toBe(false);

    survol(menu, 'pointerleave', 'mouse');
    vi.advanceTimersByTime(DELAI_FERMETURE_MS + 10);
    fixture.detectChanges();
    expect(panneau().hidden).toBe(true);
  });

  it('ignore le survol simulé au toucher : le clic ouvre et referme, Échap ferme', async () => {
    const fixture = await rendre(MegaMenu);
    vi.useFakeTimers();
    const menu: HTMLElement = fixture.nativeElement;
    const entree = menu.querySelector<HTMLButtonElement>('[data-categorie="ordinateurs"]')!;

    survol(entree, 'pointerenter', 'touch');
    vi.advanceTimersByTime(DELAI_OUVERTURE_MS * 2);
    fixture.detectChanges();
    expect(entree.getAttribute('aria-expanded')).toBe('false');

    entree.click();
    fixture.detectChanges();
    expect(entree.getAttribute('aria-expanded')).toBe('true');
    expect(menu.querySelector('#panneau-ordinateurs a[href="/categorie/pc-portables"]')).not.toBeNull();
    expect(menu.querySelector('#panneau-ordinateurs a[href="/marque/asus"]')).not.toBeNull();

    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(entree.getAttribute('aria-expanded')).toBe('false');
  });
});
