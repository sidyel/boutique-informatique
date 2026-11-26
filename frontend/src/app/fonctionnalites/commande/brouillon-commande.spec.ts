import {
  aDesErreurs,
  brouillonVide,
  erreursCoordonnees,
  erreursLivraison,
  erreursPaiement,
  lireBrouillon,
} from './brouillon-commande';

describe('lireBrouillon', () => {
  it('relit un brouillon enregistré', () => {
    const b = { ...brouillonVide(), nom: 'Awa Diop', telephone: '77 123 45 67', zoneId: 4, modePaiement: 'MOBILE_MONEY' };
    expect(lireBrouillon(JSON.stringify(b))).toEqual(b);
  });

  it.each([null, '', '{pas du json', 'null', '42', '"texte"', '[]'])('revient au brouillon vide pour %j', (brut) => {
    expect(lireBrouillon(brut)).toEqual(brouillonVide());
  });

  it('écarte les valeurs de mauvais type, inconnues ou trop longues', () => {
    const lu = lireBrouillon(
      JSON.stringify({
        nom: 42,
        telephone: 'x'.repeat(500),
        modeLivraison: 'DRONE',
        zoneId: '7',
        modePaiement: 'BITCOIN',
        adresse: 'Rue 10',
      }),
    );

    expect(lu.nom).toBe('');
    expect(lu.telephone).toHaveLength(30);
    expect(lu.modeLivraison).toBe('DOMICILE');
    expect(lu.zoneId).toBeNull();
    expect(lu.modePaiement).toBeNull();
    expect(lu.adresse).toBe('Rue 10');
  });
});

describe('validation des étapes', () => {
  it('coordonnées : nom et téléphone obligatoires, e-mail facultatif', () => {
    expect(Object.keys(erreursCoordonnees(brouillonVide()))).toEqual(['nom', 'telephone']);
    const ok = { ...brouillonVide(), nom: 'Awa', telephone: '77 123 45 67' };
    expect(aDesErreurs(erreursCoordonnees(ok))).toBe(false);
    expect(erreursCoordonnees({ ...ok, email: 'pas-un-email' }).email).toBeDefined();
    expect(aDesErreurs(erreursCoordonnees({ ...ok, email: 'awa@exemple.sn' }))).toBe(false);
  });

  it('livraison à domicile : zone, quartier, adresse ou point de repère', () => {
    const b = brouillonVide();
    expect(Object.keys(erreursLivraison(b)).sort()).toEqual(['adresse', 'quartier', 'zone']);
    const complet = { ...b, zoneId: 1, quartier: 'Médina', pointDeRepere: 'Près de la mosquée' };
    expect(aDesErreurs(erreursLivraison(complet))).toBe(false);
    expect(erreursLivraison({ ...complet, telephoneLivraison: '123' }).telephoneLivraison).toBeDefined();
  });

  it('retrait en boutique : rien à saisir', () => {
    expect(aDesErreurs(erreursLivraison({ ...brouillonVide(), modeLivraison: 'RETRAIT_MAGASIN' }))).toBe(false);
  });

  it('paiement : un mode disponible', () => {
    const b = { ...brouillonVide(), modePaiement: 'A_LA_LIVRAISON' as const };
    expect(aDesErreurs(erreursPaiement(b, ['A_LA_LIVRAISON']))).toBe(false);
    expect(erreursPaiement(b, ['MOBILE_MONEY']).paiement).toBeDefined();
    expect(erreursPaiement(brouillonVide(), ['MOBILE_MONEY']).paiement).toBeDefined();
  });
});
