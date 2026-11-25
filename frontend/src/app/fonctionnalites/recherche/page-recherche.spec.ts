import { HttpRequest, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { ProduitResume, ResultatRecherche } from '../../noyau/api/modeles';
import { SuiviService } from '../../noyau/suivi/suivi.service';
import { PageMarque } from '../marque/page-marque';
import { PageRecherche } from './page-recherche';

function produit(id: number): ProduitResume {
  return {
    id,
    slug: `produit-${id}`,
    nom: `Produit ${id}`,
    marque: { nom: 'Lenovo', slug: 'lenovo' },
    categorie: { nom: 'PC portables', slug: 'pc-portables' },
    image: null,
    varianteId: 100 + id,
    skuParDefaut: `SKU-${id}`,
    prix: 350000,
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

function resultat(champs: Partial<ResultatRecherche>): ResultatRecherche {
  return {
    requete: 'lenovo thinkpd',
    approximatif: false,
    elements: [produit(1), produit(2)],
    page: 1,
    taille: 24,
    totalElements: 2,
    totalPages: 1,
    categories: [{ valeur: 'pc-portables', libelle: 'PC portables', nombre: 2, selectionne: false }],
    marques: [{ valeur: 'lenovo', libelle: 'Lenovo', nombre: 2, selectionne: false }],
    suggestions: null,
    ...champs,
  };
}

async function preparer(reponse: (requete: HttpRequest<unknown>) => object) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'recherche', component: PageRecherche },
        { path: 'marque/:slug', component: PageMarque },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
    ],
  });
  const harness = await RouterTestingHarness.create();
  const http = TestBed.inject(HttpTestingController);
  const urls: string[] = [];
  const naviguer = async (url: string) => {
    void harness.navigateByUrl(url);
    for (let i = 0; i < 8; i++) {
      await new Promise((r) => setTimeout(r));
      for (const requete of http.match(() => true)) {
        urls.push(requete.request.urlWithParams);
        requete.flush(reponse(requete.request));
      }
    }
    await harness.fixture.whenStable();
  };
  return { naviguer, urls, element: () => harness.routeNativeElement as HTMLElement };
}

describe('PageRecherche', () => {
  it('signale une orthographe approchée, propose catégories et marques, et suit la recherche une fois', async () => {
    const rechercher = vi.spyOn(SuiviService.prototype, 'rechercher');
    const { naviguer, urls, element } = await preparer((r) =>
      r.url.startsWith('/api/recherche') ? resultat({ approximatif: true }) : {},
    );
    await naviguer('/recherche?q=lenovo%20%20thinkpd');

    expect(element().querySelector('h1')?.textContent?.replace(/ /g, ' ')).toContain('Résultats pour « lenovo thinkpd »');
    expect(element().querySelector('.approche')?.textContent).toContain('orthographe approchée');
    expect(element().querySelectorAll('bi-carte-produit').length).toBe(2);
    const legendes = [...element().querySelectorAll('.liste__colonne legend')].map((l) => l.textContent?.trim());
    expect(legendes).toEqual(['Catégorie', 'Marque']);
    expect(urls.filter((u) => u.startsWith('/api/recherche'))).toEqual(['/api/recherche?q=lenovo%20thinkpd']);
    expect(rechercher).toHaveBeenCalledTimes(1);
    expect(rechercher).toHaveBeenCalledWith('lenovo thinkpd');
  });

  it('rien trouvé : rayons et produits suggérés, demande WhatsApp avec la recherche', async () => {
    const { naviguer, element } = await preparer((r) => {
      if (r.url.startsWith('/api/parametres')) return { nomSite: 'Boutique', whatsapp: '+221770000000' };
      if (r.url.startsWith('/api/recherche')) {
        return resultat({
          elements: [],
          totalElements: 0,
          totalPages: 0,
          categories: [],
          marques: [],
          suggestions: { categories: [{ nom: 'Ordinateurs', slug: 'ordinateurs' }], produits: [produit(9)] },
        });
      }
      return {};
    });
    await naviguer('/recherche?q=imprimante%203d');

    expect(element().querySelector('h1')?.textContent?.replace(/ /g, ' ')).toContain('Aucun résultat pour « imprimante 3d »');
    expect(element().querySelector('.rayon')?.getAttribute('href')).toBe('/categorie/ordinateurs');
    expect(element().querySelectorAll('bi-rangee-produits bi-carte-produit').length).toBe(1);
    const lien = element().querySelector<HTMLAnchorElement>('.demande__bouton')!;
    expect(decodeURIComponent(lien.href)).toContain('je cherche « imprimante 3d »');
  });

  it('ne lance aucune recherche pour moins de 2 caractères', async () => {
    const { naviguer, urls, element } = await preparer(() => ({}));
    await naviguer('/recherche?q=a');

    expect(urls.some((u) => u.startsWith('/api/recherche'))).toBe(false);
    expect(element().textContent).toContain('Saisissez entre 2 et 100 caractères');
  });
});

describe('PageMarque', () => {
  it('liste les produits de la marque (paramètre marque ignoré) avec les catégories en filtre', async () => {
    const { naviguer, urls, element } = await preparer((r) => {
      const chemin = r.url.split('?')[0];
      if (chemin === '/api/marques/lenovo') {
        return { nom: 'Lenovo', slug: 'lenovo', logoUrl: null, description: 'Portables et tours.', nombreProduits: 2 };
      }
      if (chemin === '/api/marques/lenovo/filtres') {
        return {
          totalProduits: 2,
          prix: null,
          sousCategories: [{ valeur: 'pc-portables', libelle: 'PC portables', nombre: 2, selectionne: true }],
          marques: [],
          etats: [],
          disponible: { enStock: 2, selectionne: false },
          caracteristiques: [],
        };
      }
      if (chemin === '/api/produits') return resultat({});
      return {};
    });
    await naviguer('/marque/lenovo?marque=hp&categorie=pc-portables');

    expect(element().querySelector('h1')?.textContent).toContain('Lenovo');
    expect(urls).toContain('/api/produits?marque=lenovo&categorie=pc-portables');
    expect(urls).toContain('/api/marques/lenovo/filtres?categorie=pc-portables');
    const categorie = element().querySelector<HTMLInputElement>('.liste__colonne input[type=checkbox]')!;
    expect(categorie.checked).toBe(true);
    expect(element().querySelector('.puce')?.textContent).toContain('PC portables');
  });
});
