import { ElementConfiance } from '../api/modeles';
import { elementsConfianceAffiches, moyensPaiement } from './parametres-site.service';

const PAIEMENT: ElementConfiance = { icone: 'PAIEMENT', titre: 'Wave, Orange Money', texte: 'Ou paiement à la livraison' };
const LIVRAISON: ElementConfiance = { icone: 'LIVRAISON', titre: 'Livraison à Dakar en 24 h', texte: null };

describe('moyens de paiement affichés', () => {
  it('sans prestataire configuré : seulement le paiement à la livraison', () => {
    expect(moyensPaiement({ mobileMoney: false, carte: false, livraison: true })).toEqual(['Paiement à la livraison']);
  });

  it('mobile money actif : Wave, Orange Money et Free Money ; carte seulement si elle est activée', () => {
    expect(moyensPaiement({ mobileMoney: true, carte: false, livraison: true })).toEqual([
      'Wave',
      'Orange Money',
      'Free Money',
      'Paiement à la livraison',
    ]);
    expect(moyensPaiement({ mobileMoney: true, carte: true, livraison: false })).toContain('Carte bancaire');
  });

  it("rien d'actif : aucun moyen", () => {
    expect(moyensPaiement({ mobileMoney: false, carte: false, livraison: false })).toEqual([]);
  });

  it("le bandeau de confiance n'annonce pas Wave ni Orange Money s'ils ne sont pas proposés", () => {
    const [paiement, autre] = elementsConfianceAffiches([PAIEMENT, LIVRAISON], {
      mobileMoney: false,
      carte: false,
      livraison: true,
    });

    expect(paiement.titre).toBe('Paiement à la livraison');
    expect(paiement.texte).not.toMatch(/Wave|Orange/);
    expect(autre).toEqual(LIVRAISON);
  });

  it('le bandeau annonce le mobile money quand il est actif, et se masque si aucun moyen ne l’est', () => {
    expect(
      elementsConfianceAffiches([PAIEMENT], { mobileMoney: true, carte: false, livraison: true })[0].titre,
    ).toBe('Wave, Orange Money');
    expect(elementsConfianceAffiches([PAIEMENT], { mobileMoney: false, carte: false, livraison: false })).toEqual([]);
  });
});
