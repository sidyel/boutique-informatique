import { Filtres } from '../../noyau/api/modeles';
import {
  CRITERES_VIDES,
  avecFiltre,
  basculerCaracteristique,
  lireCriteres,
  nombreFiltres,
  pucesActives,
  sansFiltres,
  titreListe,
  tranchesPrix,
  versParametres,
} from './criteres';

const NBSP = / | /g;
const texte = (s: string) => s.replace(NBSP, ' ');

describe('critères de liste (URL ↔ API)', () => {
  it('lit les filtres de l’URL et ignore les paramètres inconnus', () => {
    const criteres = lireCriteres({
      marque: ['hp', 'asus'],
      etat: 'NEUF',
      prixMin: '200000',
      disponible: 'true',
      'f.ram_go': ['16.0', '8'],
      tri: 'prix-croissant',
      page: '3',
      utm_source: 'facebook',
      gclid: 'abc',
    });
    expect(criteres).toEqual({
      ...CRITERES_VIDES,
      marques: ['asus', 'hp'],
      etats: ['NEUF'],
      prixMin: 200000,
      disponible: true,
      caracteristiques: { ram_go: ['16', '8'] },
      tri: 'prix-croissant',
      page: 3,
    });
  });

  it('écarte les valeurs mal formées au lieu de provoquer une erreur 400', () => {
    const criteres = lireCriteres({
      marque: ['Asus!', 'dell'],
      etat: ['NEUVE', 'OCCASION'],
      prixMin: '-5',
      prixMax: 'beaucoup',
      tri: 'au-hasard',
      page: '0',
      'f.Ram': '16',
      'f.ecran_pouces': '',
      disponible: 'oui',
    });
    expect(criteres).toEqual({ ...CRITERES_VIDES, marques: ['dell'], etats: ['OCCASION'] });
    expect(lireCriteres({ page: '501' }).page).toBe(1);
    expect(lireCriteres({ page: '2.5' }).page).toBe(1);
  });

  it('remet dans l’ordre un prix minimum supérieur au maximum', () => {
    const criteres = lireCriteres({ prixMin: '500000', prixMax: '100000' });
    expect([criteres.prixMin, criteres.prixMax]).toEqual([100000, 500000]);
  });

  it('écrit des paramètres canoniques : ordre fixe, valeurs triées, défauts omis', () => {
    const params = versParametres({
      ...CRITERES_VIDES,
      marques: ['hp', 'asus'],
      caracteristiques: { systeme: ['Windows 11 Pro'], ram_go: ['8', '16'] },
      tri: 'recommande',
      page: 1,
    });
    expect(params).toEqual({ marque: ['asus', 'hp'], 'f.ram_go': ['16', '8'], 'f.systeme': ['Windows 11 Pro'] });
    expect(Object.keys(params)).toEqual(['marque', 'f.ram_go', 'f.systeme']);
    // Recherche : la pertinence est le tri par défaut
    expect(versParametres({ ...CRITERES_VIDES, tri: 'pertinence' }, 'pertinence')).toEqual({});
    expect(versParametres({ ...CRITERES_VIDES, tri: 'recommande', page: 2 }, 'pertinence')).toEqual({
      tri: 'recommande',
      page: '2',
    });
  });

  it('fait l’aller-retour URL → critères → URL sans perte', () => {
    const url = { etat: ['NEUF', 'RECONDITIONNE'], prixMax: '450000', 'f.ram_go': ['16', '32'], page: '2' };
    expect(versParametres(lireCriteres(url))).toEqual(url);
  });

  it('revient à la première page à chaque changement de filtre et garde le tri', () => {
    const criteres = { ...CRITERES_VIDES, tri: 'nouveaute' as const, page: 4 };
    expect(avecFiltre(criteres, { disponible: true })).toMatchObject({ page: 1, tri: 'nouveaute', disponible: true });
    const avecRam = basculerCaracteristique(criteres, 'ram_go', '16');
    expect(avecRam.caracteristiques).toEqual({ ram_go: ['16'] });
    expect(basculerCaracteristique(avecRam, 'ram_go', '16').caracteristiques).toEqual({});
    expect(sansFiltres({ ...avecRam, marques: ['hp'] })).toEqual({ ...CRITERES_VIDES, tri: 'nouveaute' });
  });

  it('compte les filtres actifs et donne une puce par valeur, avec les libellés des facettes', () => {
    const criteres = lireCriteres({ marque: 'hp', prixMin: '200000', prixMax: '400000', 'f.ram_go': ['8', '16'] });
    expect(nombreFiltres(criteres)).toBe(4);
    const filtres = {
      marques: [{ valeur: 'hp', libelle: 'HP', nombre: 2, selectionne: true }],
      caracteristiques: [
        {
          cle: 'ram_go',
          libelle: 'Mémoire vive',
          valeurs: [
            { valeur: '8', libelle: '8 Go', nombre: 1, selectionne: true },
            { valeur: '16', libelle: '16 Go', nombre: 1, selectionne: true },
          ],
        },
      ],
      sousCategories: [],
      etats: [],
    } as unknown as Filtres;
    const puces = pucesActives(criteres, filtres);
    expect(puces.map((p) => texte(p.libelle))).toEqual([
      'HP',
      '200 000 – 400 000 FCFA',
      'Mémoire vive : 16 Go',
      'Mémoire vive : 8 Go',
    ]);
    expect(puces[3].sans.caracteristiques).toEqual({ ram_go: ['16'] });
    expect(puces[1].sans).toMatchObject({ prixMin: null, prixMax: null });
  });
});

describe('tranches de prix', () => {
  it('propose 3 à 5 tranches aux bornes rondes', () => {
    const tranches = tranchesPrix(245_000, 889_000);
    expect(tranches.map((t) => texte(t.libelle))).toEqual([
      "Jusqu'à 400 000",
      '400 000 – 600 000',
      '600 000 – 800 000',
      '800 000 et plus',
    ]);
    expect(tranches[0]).toMatchObject({ prixMin: null, prixMax: 400_000 });
    expect(tranches[3]).toMatchObject({ prixMin: 800_000, prixMax: null });
  });

  it('reste entre 3 et 5 tranches quelle que soit la plage', () => {
    for (const [min, max] of [
      [5_000, 12_000_000],
      [9_900, 35_000],
      [1_000, 1_000_000],
      [150_000, 2_500_000],
      [12_500, 64_990],
    ]) {
      const tranches = tranchesPrix(min, max);
      expect(tranches.length, `${min}–${max}`).toBeGreaterThanOrEqual(3);
      expect(tranches.length, `${min}–${max}`).toBeLessThanOrEqual(5);
      for (const t of tranches) {
        for (const borne of [t.prixMin, t.prixMax]) {
          if (borne !== null) expect(borne % 5_000, `${borne}`).toBe(0);
        }
      }
    }
  });

  it('ne propose rien pour une plage trop étroite', () => {
    expect(tranchesPrix(300_000, 300_000)).toEqual([]);
    expect(tranchesPrix(300_000, 304_000)).toEqual([]);
  });
});

describe('titre des pages de liste', () => {
  it('tient en 60 caractères avec le nom du site, sinon raccourcit', () => {
    expect(titreListe('PC portables', 'Boutique', null)).toBe('PC portables au Sénégal : prix en FCFA');
    const long = 'Cartes graphiques et accessoires de refroidissement';
    expect(titreListe(long, 'Boutique', null)).toBe(long);
    const moyen = 'Disques durs et SSD externes';
    expect(titreListe(moyen, 'Boutique', null)).toBe(`${moyen} au Sénégal`);
    for (const nom of ['PC portables', moyen]) {
      expect(`${titreListe(nom, 'Boutique', null)} | Boutique`.length).toBeLessThanOrEqual(60);
    }
  });

  it('donne la priorité au meta-titre saisi par l’administrateur', () => {
    expect(titreListe('PC portables', 'Boutique', '  Ordinateurs portables pas chers  ')).toBe(
      'Ordinateurs portables pas chers',
    );
  });
});
