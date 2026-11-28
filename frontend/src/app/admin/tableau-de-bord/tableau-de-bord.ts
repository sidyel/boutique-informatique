import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { SeoService } from '../../noyau/seo/seo.service';

/**
 * Coquille du tableau de bord d'administration (contenu en phase 6).
 */
@Component({
  selector: 'bi-tableau-de-bord',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="conteneur tableau-de-bord">
      <h1>Administration</h1>
      <p>Vous êtes connecté. Les écrans de gestion arrivent par étapes (commandes, produits, clients).</p>
    </section>
  `,
  styles: `
    .tableau-de-bord {
      padding-block: var(--espace-8);
    }
  `,
})
export class TableauDeBord {
  constructor() {
    inject(SeoService).definirPage({ titre: 'Administration', indexable: false });
  }
}
