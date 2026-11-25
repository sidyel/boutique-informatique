import { PrixFcfaPipe, formaterPrixFcfa } from './prix-fcfa.pipe';

/** Remplace les espaces insécables par des espaces simples pour des attentes lisibles. */
const lisible = (texte: string) => texte.replace(/ /g, ' ');

describe('formaterPrixFcfa', () => {
  it.each([
    [450000, '450 000 FCFA'],
    [1299000, '1 299 000 FCFA'],
    [15000, '15 000 FCFA'],
    [999, '999 FCFA'],
    [0, '0 FCFA'],
    [1000000000, '1 000 000 000 FCFA'],
  ])('%d → « %s »', (montant, attendu) => {
    expect(lisible(formaterPrixFcfa(montant))).toBe(attendu);
  });

  it('utilise une espace insécable normale (U+00A0) pour les milliers et avant FCFA, jamais U+202F', () => {
    expect(formaterPrixFcfa(1450000)).toBe('1 450 000 FCFA');
    expect(formaterPrixFcfa(1450000)).not.toContain(' ');
  });

  it('arrondit les montants non entiers (sécurité : les montants doivent déjà être entiers)', () => {
    expect(lisible(formaterPrixFcfa(1499.6))).toBe('1 500 FCFA');
  });

  it('gère les montants négatifs (remises, avoirs)', () => {
    expect(lisible(formaterPrixFcfa(-25000))).toBe('−25 000 FCFA');
  });

  it('renvoie une chaîne vide pour une valeur absente ou invalide', () => {
    expect(formaterPrixFcfa(null)).toBe('');
    expect(formaterPrixFcfa(undefined)).toBe('');
    expect(formaterPrixFcfa(Number.NaN)).toBe('');
  });

  it('est utilisable comme pipe', () => {
    expect(lisible(new PrixFcfaPipe().transform(75000))).toBe('75 000 FCFA');
  });
});
