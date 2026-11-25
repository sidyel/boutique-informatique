import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Suggestions } from '../../noyau/api/modeles';
import { SuiviService } from '../../noyau/suivi/suivi.service';
import { BarreRecherche } from './barre-recherche';

const SUGGESTIONS: Suggestions = {
  produits: [
    {
      id: 1,
      slug: 'asus-tuf-a15',
      nom: 'Asus TUF Gaming A15',
      varianteId: 42,
      prix: 649000,
      image: null,
    } as Suggestions['produits'][number],
  ],
  categories: [{ nom: 'PC portables gaming', slug: 'pc-portables-gaming' }],
  marques: [{ nom: 'Asus', slug: 'asus' }],
};

function configurer() {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const fixture = TestBed.createComponent(BarreRecherche);
  fixture.componentRef.setInput('ident', 'essai');
  fixture.detectChanges();
  const champ = fixture.nativeElement.querySelector('input') as HTMLInputElement;
  const saisir = (texte: string) => {
    champ.value = texte;
    champ.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const touche = (key: string) => {
    champ.dispatchEvent(new KeyboardEvent('keydown', { key, cancelable: true }));
    fixture.detectChanges();
  };
  return { fixture, champ, saisir, touche, http: TestBed.inject(HttpTestingController) };
}

describe('BarreRecherche', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('attend la fin de la frappe, annule la requête précédente et ignore une saisie trop courte', () => {
    const { saisir, http } = configurer();

    saisir('a');
    vi.advanceTimersByTime(300);
    http.expectNone((r) => r.url === '/api/recherche/suggestions');

    saisir('as');
    vi.advanceTimersByTime(100);
    saisir('asu');
    vi.advanceTimersByTime(100);
    http.expectNone((r) => r.url === '/api/recherche/suggestions');
    vi.advanceTimersByTime(200);
    const premiere = http.expectOne('/api/recherche/suggestions?q=asu');

    saisir('asus');
    vi.advanceTimersByTime(300);
    const seconde = http.expectOne('/api/recherche/suggestions?q=asus');
    expect(premiere.cancelled).toBe(true);
    seconde.flush(SUGGESTIONS);
  });

  it('se pilote au clavier selon le motif combobox et ouvre la suggestion choisie', () => {
    const { fixture, champ, saisir, touche, http } = configurer();
    const naviguer = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    saisir('asus');
    vi.advanceTimersByTime(300);
    http.expectOne('/api/recherche/suggestions?q=asus').flush(SUGGESTIONS);
    fixture.detectChanges();

    expect(champ.getAttribute('role')).toBe('combobox');
    expect(champ.getAttribute('aria-expanded')).toBe('true');
    expect(champ.getAttribute('inputmode')).toBe('search');
    const options = fixture.nativeElement.querySelectorAll('[role="option"]');
    expect(options).toHaveLength(4); // produit, catégorie, marque, « tous les résultats »
    expect(options[0].getAttribute('href')).toBe('/produit/asus-tuf-a15?variante=42');

    touche('ArrowDown');
    touche('ArrowDown');
    expect(champ.getAttribute('aria-activedescendant')).toBe('essai-categorie-0');
    touche('Enter');
    expect(naviguer).toHaveBeenCalledWith(['/categorie', 'pc-portables-gaming'], { queryParams: undefined });
    expect(champ.getAttribute('aria-expanded')).toBe('false');
  });

  it('valide la recherche complète et la signale au suivi', () => {
    const { fixture, saisir } = configurer();
    const naviguer = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const rechercher = vi.spyOn(TestBed.inject(SuiviService), 'rechercher');

    saisir('  rtx 4060 ');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));

    expect(rechercher).toHaveBeenCalledWith('rtx 4060');
    expect(naviguer).toHaveBeenCalledWith(['/recherche'], { queryParams: { q: 'rtx 4060' } });
  });
});
