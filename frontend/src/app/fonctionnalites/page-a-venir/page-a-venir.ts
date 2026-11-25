import { ChangeDetectionStrategy, Component, effect, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ParametresSiteService } from '../../noyau/donnees/parametres-site.service';
import { SeoService } from '../../noyau/seo/seo.service';

/**
 * Page provisoire des fonctions des phases suivantes (compte : phase 5, panier : phase 4) : les liens de
 * l'en-tête ne mènent pas à une erreur 404, et le client sait comment commander dès aujourd'hui.
 * Non indexée. Titre et texte fournis par la route (`data`).
 */
@Component({
  selector: 'bi-page-a-venir',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="conteneur page">
      <h1>{{ titre() }}</h1>
      <p class="texte">{{ texte() }}</p>
      <p class="actions">
        @if (site.lienWhatsApp(); as wa) {
          <a [href]="wa" target="_blank" rel="noopener">Commander sur WhatsApp</a>
        }
        <a routerLink="/">Retour à l'accueil</a>
      </p>
    </section>
  `,
  styles: `
    .page {
      padding-block: var(--espace-12);
    }

    .texte {
      max-width: 60ch;
      margin-block-start: var(--espace-4);
      color: var(--couleur-texte-secondaire);
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--espace-6);
      margin-block-start: var(--espace-6);

      a {
        display: inline-flex;
        align-items: center;
        min-height: var(--cible-tactile);
        font-weight: var(--graisse-semi-gras);
      }
    }
  `,
})
export class PageAVenir {
  protected readonly site = inject(ParametresSiteService);

  /** Données de la route (withComponentInputBinding). */
  readonly titre = input.required<string>();
  readonly texte = input.required<string>();

  constructor() {
    const seo = inject(SeoService);
    effect(() => seo.definirPage({ titre: this.titre(), indexable: false }));
  }
}
