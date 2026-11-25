import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ProduitResume } from '../api/modeles';
import { RecemmentConsultesService } from './recemment-consultes.service';

const CLE = 'bi-recemment-consultes';

function resume(id: number, varianteId = id * 10): ProduitResume {
  return {
    id,
    slug: `produit-${id}`,
    nom: `Produit ${id}`,
    marque: { nom: 'Marque', slug: 'marque' },
    prix: 100_000,
    varianteId,
  } as ProduitResume;
}

function service(plateforme: 'browser' | 'server'): RecemmentConsultesService {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [{ provide: PLATFORM_ID, useValue: plateforme }] });
  return TestBed.inject(RecemmentConsultesService);
}

describe('RecemmentConsultesService', () => {
  beforeEach(() => localStorage.clear());

  it('garde chaque produit une seule fois, le plus récent en tête, 12 au plus', () => {
    const s = service('browser');
    for (let id = 1; id <= 14; id++) s.ajouter(resume(id));
    s.ajouter(resume(5, 51));

    const liste = s.produits();
    expect(liste).toHaveLength(12);
    expect(liste[0]).toMatchObject({ id: 5, varianteId: 51 });
    expect(liste.filter((p) => p.id === 5)).toHaveLength(1);
    expect(JSON.parse(localStorage.getItem(CLE)!)).toHaveLength(12);
  });

  it('relit la liste au démarrage et écarte un contenu invalide', () => {
    localStorage.setItem(CLE, JSON.stringify([resume(1), { id: 'x' }, null, resume(2)]));
    expect(service('browser').produits().map((p) => p.id)).toEqual([1, 2]);

    localStorage.setItem(CLE, '{pas du json');
    expect(service('browser').produits()).toEqual([]);
  });

  it('ne touche jamais au stockage pendant le rendu serveur', () => {
    localStorage.setItem(CLE, JSON.stringify([resume(1)]));
    const s = service('server');
    expect(s.produits()).toEqual([]);
    s.ajouter(resume(2));
    expect(JSON.parse(localStorage.getItem(CLE)!)).toHaveLength(1);
  });
});
