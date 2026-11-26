import { Avis, FicheProduit, Page, ParametresPublics, VarianteFiche } from '../api/modeles';
import {
  boutique,
  filAriane,
  graphe,
  listeProduits,
  organisation,
  politiqueRetour,
  produit,
  serialiserLd,
  siteWeb,
} from './donnees-structurees';

const URL = 'https://boutique.sn';

const PARAMETRES = {
  nomSite: 'Boutique',
  logoUrl: '/logo.png',
  imagePartageUrl: 'https://res.cloudinary.com/b/partage.jpg',
  telephone: '+221338000000',
  email: 'contact@boutique.sn',
  liensReseauxSociaux: { facebook: 'https://www.facebook.com/boutique', instagram: 'https://www.instagram.com/' },
  politiqueRetour: { acceptes: true, delaiJours: 7, frais: 'GRATUIT', enBoutique: true, parLivreur: false },
  adresseStructuree: null,
  boutiquePhysique: null,
} as unknown as ParametresPublics;

function variante(id: number, attributs: Record<string, string>, autres: Partial<VarianteFiche> = {}): VarianteFiche {
  return {
    id,
    sku: `SKU-${id}`,
    libelle: Object.values(attributs).join(' / '),
    attributs,
    prix: 589000,
    prixBarre: null,
    reduction: null,
    disponibilite: 'EN_STOCK',
    poidsGrammes: null,
    caracteristiques: [],
    ...autres,
  };
}

function fiche(variantes: VarianteFiche[], autres: Partial<FicheProduit> = {}): FicheProduit {
  return {
    id: 42,
    slug: 'macbook-air-13-m2',
    nom: 'MacBook Air 13 M2',
    descriptionCourte: 'Puce M2.',
    descriptionLongue: null,
    etat: 'NEUF',
    garantieMois: 12,
    gtin: '0194253081234',
    mpn: 'MLY33',
    metaTitre: null,
    metaDescription: null,
    marque: { nom: 'Apple', slug: 'apple', logoUrl: null },
    categorie: { nom: 'PC portables', slug: 'pc-portables' },
    filAriane: [],
    prixMinimum: 589000,
    disponibilite: 'EN_STOCK',
    noteMoyenne: null,
    nombreAvis: 0,
    variantes,
    medias: [],
    caracteristiques: [],
    ...autres,
  };
}

const AVIS: Page<Avis> = {
  elements: [
    {
      id: 1,
      auteur: 'Moussa D.',
      achatVerifie: true,
      note: 5,
      titre: 'Parfait',
      commentaire: 'Livré le lendemain.',
      date: '2026-11-24T23:15:10Z',
      reponseBoutique: null,
      dateReponse: null,
    },
  ],
  page: 1,
  taille: 5,
  totalElements: 7,
  totalPages: 2,
};

const images = () => ['/demo/images/macbook.webp'];

describe('données structurées', () => {
  it('produit à une seule variante : Product avec son offre en XOF, sans avis', () => {
    const ld = produit(fiche([variante(7, {})]), 'Apple MacBook Air 13 M2', images, undefined, URL);
    expect(ld).toMatchObject({
      '@type': 'Product',
      name: 'Apple MacBook Air 13 M2',
      url: 'https://boutique.sn/produit/macbook-air-13-m2',
      image: ['https://boutique.sn/demo/images/macbook.webp'],
      sku: 'SKU-7',
      gtin: '0194253081234',
      brand: { '@type': 'Brand', name: 'Apple' },
      offers: {
        '@type': 'Offer',
        url: 'https://boutique.sn/produit/macbook-air-13-m2',
        price: 589000,
        priceCurrency: 'XOF',
        availability: 'https://schema.org/InStock',
        itemCondition: 'https://schema.org/NewCondition',
        hasMerchantReturnPolicy: { '@id': 'https://boutique.sn/#politique-retour' },
      },
    });
    expect(ld).not.toHaveProperty('aggregateRating');
    expect(ld).not.toHaveProperty('review');
    expect(ld['offers']).not.toHaveProperty('shippingDetails');
  });

  it('plusieurs variantes : ProductGroup, une offre par variante avec son URL ?variante=', () => {
    const variantes = [
      variante(7, { couleur: 'Minuit', ram: '8 Go' }),
      variante(8, { couleur: 'Lumière stellaire', ram: '8 Go' }, { disponibilite: 'RUPTURE', prix: 599000 }),
      variante(9, { couleur: 'Minuit', ram: '16 Go' }, { disponibilite: 'STOCK_FAIBLE', prix: 689000, prixBarre: 729000 }),
    ];
    const ld = produit(fiche(variantes, { etat: 'RECONDITIONNE' }), 'Apple MacBook Air 13 M2', images, undefined, URL);
    expect(ld['@type']).toBe('ProductGroup');
    expect(ld['productGroupID']).toBe('42');
    expect(ld['variesBy']).toEqual(['https://schema.org/color']);
    const declinaisons = ld['hasVariant'] as Record<string, any>[];
    expect(declinaisons.map((d) => d['sku'])).toEqual(['SKU-7', 'SKU-8', 'SKU-9']);
    expect(declinaisons[1]['name']).toBe('Apple MacBook Air 13 M2 – Lumière stellaire / 8 Go');
    expect(declinaisons[1]['color']).toBe('Lumière stellaire');
    expect(declinaisons[1]['offers']).toMatchObject({
      url: 'https://boutique.sn/produit/macbook-air-13-m2?variante=8',
      price: 599000,
      availability: 'https://schema.org/OutOfStock',
      itemCondition: 'https://schema.org/RefurbishedCondition',
    });
    expect(declinaisons[2]['offers']).toMatchObject({
      availability: 'https://schema.org/LimitedAvailability',
      priceSpecification: { priceType: 'https://schema.org/StrikethroughPrice', price: 729000, priceCurrency: 'XOF' },
    });
  });

  it('sans couleur différente, variesBy est absent (mémoire et stockage non reconnus par Google)', () => {
    const ld = produit(
      fiche([variante(1, { ram: '8 Go' }), variante(2, { ram: '16 Go' })]),
      'PC',
      images,
      undefined,
      URL,
    );
    expect(ld).not.toHaveProperty('variesBy');
  });

  it('note et avis seulement quand des avis sont affichés sur la page', () => {
    const avecNote = fiche([variante(7, {})], { noteMoyenne: 4.43, nombreAvis: 7 });
    const ld = produit(avecNote, 'MacBook', images, AVIS, URL);
    expect(ld['aggregateRating']).toEqual({
      '@type': 'AggregateRating',
      ratingValue: 4.4,
      reviewCount: 7,
      bestRating: 5,
      worstRating: 1,
    });
    expect(ld['review']).toEqual([
      {
        '@type': 'Review',
        author: { '@type': 'Person', name: 'Moussa D.' },
        datePublished: '2026-11-24',
        name: 'Parfait',
        reviewBody: 'Livré le lendemain.',
        reviewRating: { '@type': 'Rating', ratingValue: 5, bestRating: 5, worstRating: 1 },
      },
    ]);
    // Avis pas (encore) chargés : rien
    expect(produit(avecNote, 'MacBook', images, undefined, URL)).not.toHaveProperty('aggregateRating');
  });

  it('organisation : identité, contact, réseaux sociaux valides et politique de retour', () => {
    const ld = organisation(PARAMETRES, 'Boutique', URL);
    expect(ld).toMatchObject({
      '@type': 'Organization',
      '@id': 'https://boutique.sn/#organisation',
      url: 'https://boutique.sn/',
      logo: 'https://boutique.sn/logo.png',
      telephone: '+221338000000',
      // Lien sans page de profil (« https://www.instagram.com/ ») écarté
      sameAs: ['https://www.facebook.com/boutique'],
      hasMerchantReturnPolicy: {
        '@type': 'MerchantReturnPolicy',
        applicableCountry: 'SN',
        returnPolicyCategory: 'https://schema.org/MerchantReturnFiniteReturnWindow',
        merchantReturnDays: 7,
        returnMethod: 'https://schema.org/ReturnInStore',
        returnFees: 'https://schema.org/FreeReturn',
      },
    });
    expect(ld).not.toHaveProperty('address');
  });

  it('retours refusés : MerchantReturnNotPermitted, sans délai', () => {
    const p = { ...PARAMETRES, politiqueRetour: { ...PARAMETRES.politiqueRetour!, acceptes: false, delaiJours: null } };
    expect(politiqueRetour(p, URL)).toEqual({
      '@type': 'MerchantReturnPolicy',
      '@id': 'https://boutique.sn/#politique-retour',
      applicableCountry: 'SN',
      returnPolicyCategory: 'https://schema.org/MerchantReturnNotPermitted',
    });
  });

  it('Store seulement avec une boutique physique et une adresse complète', () => {
    expect(boutique(PARAMETRES, 'Boutique', URL)).toBeNull();

    const avecBoutique = {
      ...PARAMETRES,
      adresseStructuree: { rue: 'Route de Ouakam', ville: 'Dakar', region: 'Dakar', codePostal: null },
      boutiquePhysique: {
        latitude: 14.716677,
        longitude: -17.467686,
        horaires: [{ jours: ['MONDAY', 'SATURDAY'], ouverture: '09:00', fermeture: '19:00' }],
      },
    } as ParametresPublics;
    expect(boutique(avecBoutique, 'Boutique', URL)).toMatchObject({
      '@type': 'Store',
      address: { '@type': 'PostalAddress', streetAddress: 'Route de Ouakam', addressCountry: 'SN' },
      geo: { '@type': 'GeoCoordinates', latitude: 14.716677, longitude: -17.467686 },
      openingHoursSpecification: [
        { dayOfWeek: ['https://schema.org/Monday', 'https://schema.org/Saturday'], opens: '09:00', closes: '19:00' },
      ],
      parentOrganization: { '@id': 'https://boutique.sn/#organisation' },
    });
    // L'adresse sert aussi à l'organisation
    expect(organisation(avecBoutique, 'Boutique', URL)['address']).toMatchObject({ addressLocality: 'Dakar' });
  });

  it('fil d’Ariane (Accueil en tête, page courante sans lien) et liste paginée', () => {
    expect(filAriane([{ nom: 'Ordinateurs', lien: '/categorie/ordinateurs' }, { nom: 'MacBook' }], URL)).toEqual({
      '@type': 'BreadcrumbList',
      itemListElement: [
        { '@type': 'ListItem', position: 1, name: 'Accueil', item: 'https://boutique.sn/' },
        { '@type': 'ListItem', position: 2, name: 'Ordinateurs', item: 'https://boutique.sn/categorie/ordinateurs' },
        { '@type': 'ListItem', position: 3, name: 'MacBook' },
      ],
    });
    const liste = listeProduits([{ slug: 'a', nom: 'A' } as never], URL, 24);
    expect(liste['itemListElement']).toEqual([
      { '@type': 'ListItem', position: 25, url: 'https://boutique.sn/produit/a', name: 'A' },
    ]);
    expect(siteWeb('Boutique', URL)).toMatchObject({ '@type': 'WebSite', name: 'Boutique', url: 'https://boutique.sn/' });
  });

  it('sérialisation : un texte ne peut pas fermer la balise script', () => {
    const texte = serialiserLd(graphe([{ '@type': 'Thing', name: '</script><script>alert(1)</script> & co' }, null]));
    expect(texte).not.toContain('<');
    expect(texte).not.toContain('>');
    expect(JSON.parse(texte)).toEqual({
      '@context': 'https://schema.org',
      '@graph': [{ '@type': 'Thing', name: '</script><script>alert(1)</script> & co' }],
    });
  });
});
