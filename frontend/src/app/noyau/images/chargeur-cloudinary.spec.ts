import { chargeurCloudinary, srcsetCloudinary } from './chargeur-cloudinary';

const ENREGISTREE =
  'https://res.cloudinary.com/boutique-sn/image/upload/f_auto,q_auto/v1764000000/boutique/produits/12/asus-tuf.jpg';

describe('chargeurCloudinary', () => {
  it('complète f_auto,q_auto avec la largeur, une seule fois, version et idPublic intacts', () => {
    const url = chargeurCloudinary({ src: ENREGISTREE, width: 640 });

    expect(url).toBe(
      'https://res.cloudinary.com/boutique-sn/image/upload/f_auto,q_auto,w_640/v1764000000/boutique/produits/12/asus-tuf.jpg',
    );
    expect(url.match(/f_auto/g)).toHaveLength(1);
    expect(url.match(/w_\d+/g)).toHaveLength(1);
  });

  it('remplace une largeur déjà présente au lieu de l’empiler', () => {
    const deja = ENREGISTREE.replace('f_auto,q_auto', 'f_auto,q_auto,w_1200');
    expect(chargeurCloudinary({ src: deja, width: 384 })).toBe(
      ENREGISTREE.replace('f_auto,q_auto', 'f_auto,q_auto,w_384'),
    );
  });

  it('complète la miniature d’une vidéo', () => {
    const miniature =
      'https://res.cloudinary.com/boutique-sn/video/upload/so_auto,f_auto,q_auto/v17/boutique/produits/12/videos/demo.jpg';
    expect(chargeurCloudinary({ src: miniature, width: 320 })).toContain(
      '/video/upload/so_auto,f_auto,q_auto,w_320/v17/',
    );
  });

  it('laisse inchangés le SVG nettoyé, une image de démonstration et une URL sans largeur demandée', () => {
    const svg = 'https://res.cloudinary.com/boutique-sn/image/upload/fl_sanitize/v3/boutique/site/logo.svg';
    expect(chargeurCloudinary({ src: svg, width: 200 })).toBe(svg);
    expect(chargeurCloudinary({ src: '/demo/images/pc-portable.svg', width: 640 })).toBe(
      '/demo/images/pc-portable.svg',
    );
    expect(chargeurCloudinary({ src: ENREGISTREE })).toBe(ENREGISTREE);
  });

  it('ajoute la transformation de diffusion à une URL Cloudinary qui n’en a pas', () => {
    const brute = 'https://res.cloudinary.com/boutique-sn/image/upload/v9/boutique/marques/asus.png';
    expect(chargeurCloudinary({ src: brute, width: 96 })).toBe(
      'https://res.cloudinary.com/boutique-sn/image/upload/f_auto,q_auto,w_96/v9/boutique/marques/asus.png',
    );
  });

  it('construit le srcset d’un <source>, ou aucun pour une image sans déclinaison', () => {
    expect(srcsetCloudinary(ENREGISTREE, [640, 960])).toBe(
      `${chargeurCloudinary({ src: ENREGISTREE, width: 640 })} 640w, ${chargeurCloudinary({ src: ENREGISTREE, width: 960 })} 960w`,
    );
    expect(srcsetCloudinary('/demo/images/banniere-gaming-mobile.svg', [640, 960])).toBeNull();
  });
});
