import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { SeoService } from '../../noyau/seo/seo.service';

/**
 * Vitrine du système de design (non indexée). Contenu complet à l'étape 4.
 */
@Component({
  selector: 'bi-guide-style',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="conteneur">
      <h1>Guide de style</h1>
    </section>
  `,
})
export class GuideStyle {
  constructor() {
    inject(SeoService).definirPage({ titre: 'Guide de style', indexable: false });
  }
}
