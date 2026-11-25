import { FicheProduit, GroupeCaracteristiques, MediaFiche, VarianteFiche } from '../../noyau/api/modeles';
import {
  axesVariantes,
  caracteristiquesPour,
  lireIdVariante,
  mediasPour,
  messagePlusProche,
  messageWhatsAppProduit,
  nomComplet,
  optionsAxe,
  reassurance,
  titreProduit,
  varianteInitiale,
} from './fiche';

function variante(id: number, attributs: Record<string, string>, prix: number, champs: Partial<VarianteFiche> = {}): VarianteFiche {
  return {
    id,
    sku: `SKU-${id}`,
    libelle: Object.values(attributs).join(' / '),
    attributs,
    prix,
    prixBarre: null,
    reduction: null,
    disponibilite: 'EN_STOCK',
    poidsGrammes: null,
    caracteristiques: [],
    ...champs,
  };
}

/** MacBook Air de la démonstration : 16 Go seulement en Minuit. */
const MINUIT_8 = variante(1, { ram: '8 Go', stockage: '256 Go', couleur: 'Minuit' }, 689_000);
const STELLAIRE_8 = variante(2, { ram: '8 Go', stockage: '256 Go', couleur: 'Lumière stellaire' }, 689_000);
const MINUIT_16 = variante(3, { ram: '16 Go', stockage: '512 Go', couleur: 'Minuit' }, 889_000);
const MACBOOK = [MINUIT_8, STELLAIRE_8, MINUIT_16];

describe('variantes de la fiche produit', () => {
  it('ne propose que les axes qui offrent un vrai choix, avec des libellés français', () => {
    const axes = axesVariantes(MACBOOK);
    expect(axes.map((a) => a.libelle)).toEqual(['Mémoire vive', 'Stockage', 'Couleur']);
    expect(axes[2].valeurs).toEqual(['Minuit', 'Lumière stellaire']);
    expect(axesVariantes([variante(9, { ram: '8 Go' }, 1), variante(10, { ram: '8 Go' }, 2)])).toEqual([]);
  });

  it('ouvre la variante de l’URL, sinon la moins chère en stock', () => {
    expect(varianteInitiale(MACBOOK, 3)).toBe(MINUIT_16);
    expect(varianteInitiale(MACBOOK, 999)).toBe(MINUIT_8);
    const rupture = variante(4, { ram: '8 Go' }, 100, { disponibilite: 'RUPTURE' });
    expect(varianteInitiale([rupture, MINUIT_16], null)).toBe(MINUIT_16);
    expect(lireIdVariante('12')).toBe(12);
    expect(lireIdVariante('12abc')).toBeNull();
    expect(lireIdVariante(null)).toBeNull();
  });

  it('grise une combinaison inexistante et mène à la variante la plus proche', () => {
    const axes = axesVariantes(MACBOOK);
    const couleur = axes.find((a) => a.cle === 'couleur')!;

    // Depuis 16 Go Minuit, « Lumière stellaire » n'existe pas en 16 Go
    const options = optionsAxe(MACBOOK, MINUIT_16, couleur);
    const stellaire = options.find((o) => o.valeur === 'Lumière stellaire')!;
    expect(stellaire.exacte).toBe(false);
    expect(stellaire.variante).toBe(STELLAIRE_8);
    expect(options.find((o) => o.valeur === 'Minuit')).toMatchObject({ exacte: true, courante: true });
    expect(messagePlusProche(couleur, stellaire, MINUIT_16, axes)).toBe(
      "Lumière stellaire n'existe pas avec 16 Go et 512 Go : voici la version la plus proche (8 Go / 256 Go / Lumière stellaire).",
    );

    // Depuis 8 Go Minuit, « Lumière stellaire » existe : option normale
    expect(optionsAxe(MACBOOK, MINUIT_8, couleur).find((o) => o.valeur === 'Lumière stellaire')!.exacte).toBe(true);
  });

  it('garde sélectionnable une combinaison existante en rupture', () => {
    const v8 = variante(1, { ram: '8 Go' }, 300_000);
    const v16 = variante(2, { ram: '16 Go' }, 400_000, { disponibilite: 'RUPTURE' });
    const [axe] = axesVariantes([v8, v16]);

    const option16 = optionsAxe([v8, v16], v8, axe).find((o) => o.valeur === '16 Go')!;

    expect(option16).toMatchObject({ exacte: true, rupture: true, variante: v16 });
  });

  it('à égalité de proximité, préfère une variante en stock puis le prix le plus proche', () => {
    const base = variante(1, { ram: '8 Go', couleur: 'Noir' }, 300_000);
    const lointaine = variante(2, { ram: '16 Go', couleur: 'Gris' }, 900_000);
    const proche = variante(3, { ram: '16 Go', couleur: 'Bleu' }, 400_000);
    const enRupture = variante(4, { ram: '16 Go', couleur: 'Vert' }, 310_000, { disponibilite: 'RUPTURE' });
    const axe = axesVariantes([base, lointaine, proche, enRupture]).find((a) => a.cle === 'ram')!;

    const option = optionsAxe([base, lointaine, proche, enRupture], base, axe).find((o) => o.valeur === '16 Go')!;

    expect(option.exacte).toBe(false);
    expect(option.variante).toBe(proche);
  });
});

describe('médias et caractéristiques de la fiche produit', () => {
  function media(id: number, varianteId: number | null): MediaFiche {
    return {
      id,
      type: 'IMAGE',
      url: `/m${id}.webp`,
      urlMiniature: null,
      texteAlternatif: null,
      principal: id === 1,
      varianteId,
      idYoutube: null,
    };
  }

  it('montre les photos de la variante d’abord et écarte celles des autres variantes', () => {
    const medias = [media(1, null), media(2, 20), media(3, 30), media(4, null)];
    expect(mediasPour(medias, 30).map((m) => m.id)).toEqual([3, 1, 4]);
    expect(mediasPour(medias, 99).map((m) => m.id)).toEqual([1, 4]);
  });

  it('remplace les valeurs propres aux variantes dans le tableau', () => {
    const groupes: GroupeCaracteristiques[] = [
      {
        groupe: 'Mémoire',
        elements: [
          { cle: 'ram_go', libelle: 'Mémoire vive', valeur: '8', unite: 'Go', parVariante: true },
          { cle: 'ram_type', libelle: 'Type', valeur: 'DDR5', unite: null, parVariante: false },
        ],
      },
    ];
    const v16 = variante(2, { ram: '16 Go' }, 1, { caracteristiques: [{ cle: 'ram_go', valeur: '16', unite: 'Go' }] });

    const tableau = caracteristiquesPour(groupes, v16);

    expect(tableau[0].elements.map((e) => e.valeur)).toEqual(['16', 'DDR5']);
    expect(groupes[0].elements[0].valeur).toBe('8');
  });
});

describe('textes de la fiche produit', () => {
  const fiche = { nom: 'Vivobook 15', marque: { nom: 'Asus', slug: 'asus', logoUrl: null } } as FicheProduit;

  it('place la marque devant le nom sans la répéter', () => {
    expect(nomComplet(fiche)).toBe('Asus Vivobook 15');
    expect(nomComplet({ ...fiche, nom: 'ASUS TUF Gaming A15' })).toBe('ASUS TUF Gaming A15');
  });

  it('garde le titre sous 60 caractères avec le nom du site', () => {
    expect(titreProduit('Asus Vivobook 15', 'Boutique informatique', null)).toBe('Asus Vivobook 15 : prix au Sénégal');
    const long = 'Lenovo IdeaPad Slim 3 15IAN8 Intel Core i3';
    expect(titreProduit(long, 'Boutique informatique', null)).toBe(long);
    expect(titreProduit(long, 'Boutique', ' Titre saisi ')).toBe('Titre saisi');
  });

  it('prépare un message WhatsApp avec la variante, la référence, le prix et le lien', () => {
    const message = messageWhatsAppProduit('Apple MacBook Air 13 M2', MINUIT_16, '889 000 FCFA', 'https://exemple.sn/p', true);
    expect(message).toContain('Apple MacBook Air 13 M2 (16 Go / 512 Go / Minuit), réf. SKU-3, à 889 000 FCFA');
    expect(message).toContain('Est-il disponible ?');
    expect(message.endsWith('https://exemple.sn/p')).toBe(true);
    const rupture = messageWhatsAppProduit('X', { ...MINUIT_8, disponibilite: 'RUPTURE' }, '1 FCFA', 'u', false);
    expect(rupture).toContain('sera-t-il bientôt disponible ?');
    expect(rupture).not.toContain('(');
  });

  it('reprend garantie, paiement, retours et SAV sous le bloc d’achat', () => {
    const elements = reassurance([
      { icone: 'LIVRAISON', titre: 'Livraison 24 h', texte: null },
      { icone: 'GARANTIE', titre: 'Garantie 12 mois', texte: null },
      { icone: 'BOUTIQUE', titre: 'Boutique à Dakar', texte: null },
      { icone: 'RETOUR', titre: 'Retour 7 jours', texte: null },
      { icone: null, titre: 'Sans icône', texte: null },
    ]);
    expect(elements.map((e) => e.titre)).toEqual(['Garantie 12 mois', 'Retour 7 jours']);
  });
});
