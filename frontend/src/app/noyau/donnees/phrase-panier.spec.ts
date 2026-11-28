import { phraseInformationPanier } from './parametres-site.service';

const LIVRAISON = { mobileMoney: false, carte: false, livraison: true };

describe('phrase d’information du panier', () => {
  it('reprend le délai configuré et le paiement à la livraison seul', () => {
    expect(phraseInformationPanier('Livré à Dakar en 24 h, en régions en 2 à 4 jours', LIVRAISON)).toBe(
      "Livré à Dakar en 24 h, en régions en 2 à 4 jours. Paiement à la livraison : vous réglez à la réception de votre commande, après l'avoir vérifiée.",
    );
  });

  it('annonce Wave, Orange Money et Free Money seulement si le mobile money est actif', () => {
    expect(phraseInformationPanier(null, { mobileMoney: true, carte: false, livraison: true })).toBe(
      'Paiement par Wave, Orange Money, Free Money ou à la livraison.',
    );
    expect(phraseInformationPanier(null, { mobileMoney: true, carte: true, livraison: false })).toBe(
      'Paiement par Wave, Orange Money, Free Money, carte bancaire.',
    );
    expect(phraseInformationPanier(null, LIVRAISON)).not.toContain('Wave');
  });

  it('chaque partie disparaît si elle n’est pas configurée ou pas active', () => {
    expect(phraseInformationPanier('Livraison à Dakar en 24 h.', { mobileMoney: false, carte: false, livraison: false })).toBe(
      'Livraison à Dakar en 24 h.',
    );
    expect(phraseInformationPanier('  ', LIVRAISON)).toMatch(/^Paiement à la livraison/);
    expect(phraseInformationPanier(undefined, { mobileMoney: false, carte: false, livraison: false })).toBeNull();
  });
});
