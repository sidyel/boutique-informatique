# Sources des images de démonstration

Images neutres (aucune photo de fabricant) dessinées en SVG, puis rendues **une fois** en WebP
(qualité 80, taille native) et commitées dans `frontend/public/demo/images/`.

Le site sert les WebP et non les SVG : en production, les photos viennent de Cloudinary en WebP/AVIF,
décodées hors du fil principal. Un SVG utilisé comme image est analysé et dessiné sur le fil principal
du navigateur, en concurrence avec le démarrage d'Angular : les mesures Lighthouse ne ressemblaient pas
à la production.

Pour modifier une image : éditer le SVG ici, puis le rendre en WebP à sa taille native (`viewBox`),
par exemple avec Chrome sans interface (capture d'une page qui n'affiche que l'image) ou
`cwebp`/`magick`, et remplacer le fichier dans `frontend/public/demo/images/`.
