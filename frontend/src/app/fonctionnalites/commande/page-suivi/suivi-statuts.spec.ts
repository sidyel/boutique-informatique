import { parcours } from './suivi-statuts';

describe('parcours', () => {
  it('paiement en ligne : attente de paiement, paiement reçu, préparation, livraison', () => {
    const etapes = parcours('PAYEE', 'MOBILE_MONEY', 'DOMICILE');

    expect(etapes.map((e) => e.statut)).toEqual(['EN_ATTENTE_PAIEMENT', 'PAYEE', 'EN_PREPARATION', 'EXPEDIEE', 'LIVREE']);
    expect(etapes.map((e) => e.etat)).toEqual(['faite', 'courante', 'a-venir', 'a-venir', 'a-venir']);
  });

  it('paiement à la livraison : appel de confirmation puis commande confirmée', () => {
    const etapes = parcours('EN_ATTENTE_CONFIRMATION', 'A_LA_LIVRAISON', 'DOMICILE');

    expect(etapes.map((e) => e.libelle).slice(0, 2)).toEqual(['Appel de confirmation', 'Commande confirmée']);
    expect(etapes[0].etat).toBe('courante');
  });

  it('retrait en boutique : « prête à retirer » et « retirée »', () => {
    const libelles = parcours('CONFIRMEE', 'A_LA_LIVRAISON', 'RETRAIT_MAGASIN').map((e) => e.libelle);

    expect(libelles).toContain('Prête à retirer');
    expect(libelles.at(-1)).toBe('Retirée');
  });

  it('une commande livrée a toutes ses étapes faites, aucune n est en cours', () => {
    const etats = parcours('LIVREE', 'MOBILE_MONEY', 'DOMICILE').map((e) => e.etat);

    expect(etats).toEqual(['faite', 'faite', 'faite', 'faite', 'faite']);
  });

  it('annulée ou remboursée : pas de parcours', () => {
    expect(parcours('ANNULEE', 'MOBILE_MONEY', 'DOMICILE')).toEqual([]);
    expect(parcours('REMBOURSEE', 'A_LA_LIVRAISON', 'DOMICILE')).toEqual([]);
  });
});
