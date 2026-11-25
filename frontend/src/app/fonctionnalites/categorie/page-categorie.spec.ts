import { Location } from '@angular/common';
import { HttpRequest, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { Categorie, Filtres, Page, ProduitResume } from '../../noyau/api/modeles';
import { PageCategorie, paragraphes } from './page-categorie';

@Component({ template: '' })
class PageVide {}

function produit(id: number): ProduitResume {
  return {
    id,
    slug: `produit-${id}`,
    nom: `Produit ${id}`,
    marque: { nom: 'HP', slug: 'hp' },
    categorie: { nom: 'PC portables', slug: 'pc-portables' },
    image: null,
    varianteId: 100 + id,
    skuParDefaut: `SKU-${id}`,
    prix: 300000 + id,
    prixBarre: null,
    reduction: null,
    etat: 'NEUF',
    disponibilite: 'EN_STOCK',
    noteMoyenne: null,
    nombreAvis: 0,
    nombreVariantes: 1,
    caracteristiquesCles: null,
  };
}

const CATEGORIE: Categorie = {
  nom: 'PC portables',
  slug: 'pc-portables',
  description: 'Portables pour le bureau.',
  imageUrl: null,
  banniereUrl: null,
  texteSeo: 'Premier paragraphe.\n\nSecond paragraphe.',
  metaTitre: null,
  metaDescription: null,
  nombreProduits: 30,
  filAriane: [{ nom: 'Ordinateurs', slug: 'ordinateurs' }],
  enfants: [],
};

function filtres(selection: string[]): Filtres {
  return {
    totalProduits: 30,
    prix: { min: 245000, max: 889000 },
    sousCategories: [],
    marques: ['hp', 'asus'].map((m) => ({
      valeur: m,
      libelle: m.toUpperCase(),
      nombre: 15,
      selectionne: selection.includes(m),
    })),
    etats: [{ valeur: 'NEUF', libelle: 'Neuf', nombre: 30, selectionne: false }],
    disponible: { enStock: 30, selectionne: false },
    caracteristiques: [],
  };
}

/** Réponses du faux backend ; chaque URL de produits demandée est notée. */
class FauxBackend {
  readonly produitsDemandes: string[] = [];

  constructor(private readonly http: HttpTestingController) {}

  repondre(): void {
    for (const requete of this.http.match(() => true)) {
      requete.flush(this.reponse(requete.request));
    }
  }

  private reponse(requete: HttpRequest<unknown>): object {
    const url = new URL(requete.urlWithParams, 'http://test');
    if (url.pathname === '/api/categories/pc-portables') return CATEGORIE;
    if (url.pathname === '/api/categories/pc-portables/filtres') return filtres(url.searchParams.getAll('marque'));
    if (url.pathname === '/api/produits') {
      this.produitsDemandes.push(url.search);
      const page = Number(url.searchParams.get('page') ?? '1');
      const reponse: Page<ProduitResume> = {
        elements: Array.from({ length: 24 }, (_, i) => produit((page - 1) * 24 + i + 1)).slice(0, page === 2 ? 6 : 24),
        page,
        taille: 24,
        totalElements: 30,
        totalPages: 2,
      };
      return reponse;
    }
    return {};
  }
}

async function preparer() {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: '', component: PageVide },
        { path: 'categorie/:slug', component: PageCategorie },
        { path: 'produit/:slug', component: PageVide },
      ]),
      provideLocationMocks(),
      provideHttpClient(),
      provideHttpClientTesting(),
    ],
  });
  const harness = await RouterTestingHarness.create('/');
  // Hors d'une application amorcée, le routeur n'écoute pas l'historique (retour arrière) : on l'y branche
  TestBed.inject(Router).initialNavigation();
  await harness.fixture.whenStable();
  const backend = new FauxBackend(TestBed.inject(HttpTestingController));
  const location = TestBed.inject(Location);
  /**
   * Répond aux appels au fil du rendu, puis attend la stabilité (les requêtes en attente empêchent
   * l'application d'être stable : il faut y répondre avant d'attendre).
   */
  const stabiliser = async () => {
    for (let i = 0; i < 8; i++) {
      await new Promise((r) => setTimeout(r));
      backend.repondre();
    }
    await harness.fixture.whenStable();
  };
  /** Navigation sans attendre la stabilité (elle dépend des réponses du faux backend). */
  const naviguer = async (url: string) => {
    void harness.navigateByUrl(url);
    await stabiliser();
  };
  return { harness, backend, location, stabiliser, naviguer, element: () => harness.routeNativeElement as HTMLElement };
}

describe('PageCategorie', () => {
  it('affiche le rayon, ses produits, la pagination et le texte éditorial', async () => {
    const { element, naviguer } = await preparer();
    await naviguer('/categorie/pc-portables');

    expect(element().querySelector('h1')?.textContent).toContain('PC portables');
    expect(element().querySelectorAll('bi-carte-produit').length).toBe(24);
    expect(element().querySelector('.barre__compte')?.textContent).toContain('30');
    expect(element().querySelector('bi-fil-ariane')?.textContent).toContain('Ordinateurs');
    expect(element().querySelectorAll('.editorial p').length).toBe(2);
    expect(element().querySelector('a[rel="next"]')?.getAttribute('href')).toBe('/categorie/pc-portables?page=2');
  });

  it('retour arrière : un filtre remplace l’entrée d’historique, une page en crée une, la liste revient sans appel', async () => {
    const { harness, backend, location, element, stabiliser, naviguer } = await preparer();
    await naviguer('/categorie/pc-portables');

    // Filtre « HP » coché dans la colonne : l'URL change, l'entrée d'historique est remplacée
    const caseHp = [...element().querySelectorAll<HTMLInputElement>('.liste__colonne input[type=checkbox]')].find(
      (c) => c.closest('label')?.textContent?.includes('HP'),
    )!;
    caseHp.click();
    await stabiliser();
    expect(location.path()).toBe('/categorie/pc-portables?marque=hp');

    // Changement de tri : remplacé lui aussi
    const tri = element().querySelector<HTMLSelectElement>('.barre__tri select')!;
    tri.value = 'prix-croissant';
    tri.dispatchEvent(new Event('change'));
    await stabiliser();
    expect(location.path()).toBe('/categorie/pc-portables?marque=hp&tri=prix-croissant');

    // Page 2 : nouvelle entrée, filtres et tri conservés
    element().querySelector<HTMLAnchorElement>('a[rel="next"]')!.click();
    await stabiliser();
    expect(location.path()).toBe('/categorie/pc-portables?marque=hp&tri=prix-croissant&page=2');
    expect(element().querySelectorAll('bi-carte-produit').length).toBe(6);

    // Ouverture d'une fiche produit, puis retour
    element().querySelector<HTMLAnchorElement>('.carte__lien')!.click();
    await stabiliser();
    expect(location.path()).toBe('/produit/produit-25?variante=125');
    const demandesAvantRetour = backend.produitsDemandes.length;

    location.back();
    await stabiliser();
    // Même page, mêmes filtres, liste reprise de la mémoire : aucun nouvel appel de produits
    // (la position de défilement est vérifiée dans un vrai navigateur, voir le rapport de la partie 3b)
    expect(location.path()).toBe('/categorie/pc-portables?marque=hp&tri=prix-croissant&page=2');
    expect(element().querySelectorAll('bi-carte-produit').length).toBe(6);
    expect(backend.produitsDemandes.length).toBe(demandesAvantRetour);

    // Retour : page 1 avec ses filtres, puis directement l'accueil (aucune entrée par case cochée)
    location.back();
    await stabiliser();
    expect(location.path()).toBe('/categorie/pc-portables?marque=hp&tri=prix-croissant');
    location.back();
    await stabiliser();
    expect(location.path()).toBe('/');
  });

  it('propose de retirer le dernier filtre et WhatsApp quand la liste filtrée est vide', async () => {
    const { harness, element, stabiliser } = await preparer();
    void harness.navigateByUrl('/categorie/pc-portables?marque=hp&prixMin=1&prixMax=2');
    // Réponse vide pour cette combinaison
    const http = TestBed.inject(HttpTestingController);
    await new Promise((r) => setTimeout(r));
    for (const r of http.match((req) => req.url.startsWith('/api/produits'))) {
      r.flush({ elements: [], page: 1, taille: 24, totalElements: 0, totalPages: 0 });
    }
    for (const r of http.match((req) => req.url.startsWith('/api/parametres'))) {
      r.flush({ nomSite: 'Boutique', whatsapp: '+221770000000', elementsConfiance: [], liensReseauxSociaux: {} });
    }
    await stabiliser();

    expect(element().querySelector('.vide__titre')?.textContent).toContain('Aucun produit ne correspond');
    expect(element().querySelector('.vide__actions button')?.textContent).toContain('Retirer');
    const lien = element().querySelector<HTMLAnchorElement>('.demande__bouton');
    expect(lien).not.toBeNull();
    expect(decodeURIComponent(lien!.href)).toContain('catégorie « PC portables » (HP, 1 – 2');
  });
});

describe('texte éditorial', () => {
  it('découpe le texte brut en paragraphes', () => {
    expect(paragraphes('Un.\n\n  Deux.\n \nTrois.')).toEqual(['Un.', 'Deux.', 'Trois.']);
    expect(paragraphes(null)).toEqual([]);
  });
});
