import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { SeoService } from '../../noyau/seo/seo.service';

/**
 * Page d'accueil provisoire. Le carrousel, les tuiles de catégories et les sélections
 * de produits arrivent en phase 3.
 */
@Component({
  selector: 'bi-accueil',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="conteneur accueil">
      <h1>Matériel informatique au Sénégal</h1>
      <p class="chapeau">
        PC portables, PC de bureau, composants, écrans, périphériques et réseau.
        Livraison à Dakar en 24 h, paiement Wave, Orange Money ou à la livraison.
      </p>
      <p class="mention">Le catalogue est en cours de mise en ligne.</p>
    </section>
  `,
  styles: `
    .accueil {
      padding-block: var(--espace-12);
    }

    h1 {
      max-width: 20ch;
    }

    .chapeau {
      max-width: 60ch;
      margin-block-start: var(--espace-4);
      font-size: var(--taille-lg);
      color: var(--couleur-texte-secondaire);
    }

    .mention {
      margin-block-start: var(--espace-6);
      font-family: var(--police-mono);
      font-size: var(--taille-sm);
    }
  `,
})
export class Accueil {
  constructor() {
    inject(SeoService).definirPage({
      titre: 'Matériel informatique au Sénégal',
      description:
        'PC portables, composants, écrans et périphériques au meilleur prix au Sénégal. ' +
        'Livraison à Dakar en 24 h, paiement mobile ou à la livraison.',
    });
  }
}
