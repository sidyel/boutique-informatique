import { formaterTelephone, normaliserTelephone } from './telephone-senegal';

describe('normaliserTelephone', () => {
  it.each([
    ['77 123 45 67', '+221771234567'],
    ['771234567', '+221771234567'],
    ['+221 77 123 45 67', '+221771234567'],
    ['00221771234567', '+221771234567'],
    ['221771234567', '+221771234567'],
    ['(33) 821-00-00', '+221338210000'],
    ['78.123.45.67', '+221781234567'],
  ])('accepte « %s »', (saisie, attendu) => {
    expect(normaliserTelephone(saisie)).toBe(attendu);
  });

  it.each(['', '  ', '12345', '71 123 45 67', '+33 6 12 34 56 78', '77 123 45 6', '77 123 45 678', 'abcdefghi'])(
    'refuse « %s »',
    (saisie) => {
      expect(normaliserTelephone(saisie)).toBeNull();
    },
  );

  it('accepte null et undefined sans planter', () => {
    expect(normaliserTelephone(null)).toBeNull();
    expect(normaliserTelephone(undefined)).toBeNull();
  });
});

describe('formaterTelephone', () => {
  it('groupe les chiffres', () => {
    expect(formaterTelephone('+221771234567')).toBe('77 123 45 67');
  });
});
