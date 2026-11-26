import { CRITERES_VIDES, Criteres } from './criteres';
import { referencementListe } from './referencement-liste';

const avec = (changement: Partial<Criteres>): Criteres => ({ ...CRITERES_VIDES, ...changement });

describe('referencementListe', () => {
  it('page sans filtre : indexable, canonique vers elle-même', () => {
    expect(referencementListe('/categorie/pc-portables', CRITERES_VIDES)).toEqual({
      canonique: '/categorie/pc-portables',
      indexable: true,
    });
  });

  it('page 2 sans filtre : indexable, la pagination reste dans la canonique', () => {
    expect(referencementListe('/categorie/pc-portables', avec({ page: 2 }))).toEqual({
      canonique: '/categorie/pc-portables?page=2',
      indexable: true,
    });
  });

  it('page filtrée : noindex, canonique vers elle-même avec des paramètres normalisés', () => {
    const r = referencementListe('/categorie/pc-portables', avec({ marques: ['hp', 'asus'], page: 3 }));
    expect(r.indexable).toBe(false);
    expect(r.canonique).toBe('/categorie/pc-portables?marque=asus&marque=hp&page=3');
  });

  it('tri choisi : noindex ; le tri par défaut est retiré et reste indexable', () => {
    expect(referencementListe('/marque/hp', avec({ tri: 'prix-croissant' }))).toEqual({
      canonique: '/marque/hp?tri=prix-croissant',
      indexable: false,
    });
    expect(referencementListe('/marque/hp', avec({ tri: 'recommande' }))).toEqual({
      canonique: '/marque/hp',
      indexable: true,
    });
  });
});
