import { lienTelephone, lienWhatsApp, numeroAffiche } from './parametres-site.service';

describe('liens directs (tel: et wa.me)', () => {
  it('ouvre l’appel téléphonique', () => {
    expect(lienTelephone('+221338000000')).toBe('tel:+221338000000');
    expect(lienTelephone(null)).toBeNull();
  });

  it('ouvre WhatsApp directement, avec un message prérempli encodé', () => {
    expect(lienWhatsApp('+221770000000')).toBe('https://wa.me/221770000000');
    expect(lienWhatsApp('+221770000000', 'Bonjour, le TUF A15 à 649 000 FCFA ?')).toBe(
      'https://wa.me/221770000000?text=Bonjour%2C%20le%20TUF%20A15%20%C3%A0%20649%20000%20FCFA%20%3F',
    );
    expect(lienWhatsApp(undefined)).toBeNull();
  });

  it('affiche le numéro au format local', () => {
    expect(numeroAffiche('+221770000000')).toBe('77 000 00 00');
    expect(numeroAffiche('+33612345678')).toBe('+33612345678');
  });
});
