import { dateAvis, noteAffichee } from './avis-produit';

describe('affichage des avis', () => {
  it('date identique côté serveur et navigateur (UTC, sans Intl)', () => {
    expect(dateAvis('2026-11-24T23:15:10Z')).toBe('24 nov. 2026');
    expect(dateAvis('2026-02-01T00:00:00Z')).toBe('1 févr. 2026');
    expect(dateAvis('pas une date')).toBe('');
  });

  it('note moyenne à la française, sans « ,0 »', () => {
    expect(noteAffichee(4.43)).toBe('4,4');
    expect(noteAffichee(5)).toBe('5');
    expect(noteAffichee(3.96)).toBe('4');
  });
});
