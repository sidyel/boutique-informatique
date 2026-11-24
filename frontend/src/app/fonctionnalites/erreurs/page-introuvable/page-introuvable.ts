import { ChangeDetectionStrategy, Component, RESPONSE_INIT, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SeoService } from '../../../noyau/seo/seo.service';

/**
 * Page 404. Pendant le rendu serveur, le statut HTTP de la réponse passe à 404
 * (règle SEO : jamais de « soft 404 » avec un statut 200).
 */
@Component({
  selector: 'bi-page-introuvable',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="conteneur introuvable">
      <p class="code">Erreur 404</p>
      <h1>Cette page n'existe pas ou a été déplacée</h1>
      <p>
        Le produit a peut-être été retiré du catalogue, ou l'adresse contient une faute de frappe.
      </p>
      <a routerLink="/" class="lien-retour">Revenir à l'accueil</a>
    </section>
  `,
  styles: `
    .introuvable {
      padding-block: var(--espace-16);
      max-width: 48rem;
    }

    .code {
      font-family: var(--police-mono);
      font-size: var(--taille-sm);
      color: var(--couleur-accent);
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }

    h1 {
      margin-block: var(--espace-2) var(--espace-4);
    }

    .lien-retour {
      display: inline-block;
      margin-block-start: var(--espace-6);
      font-weight: var(--graisse-semi-gras);
    }
  `,
})
export class PageIntrouvable {
  constructor() {
    const reponse = inject(RESPONSE_INIT, { optional: true });
    if (reponse) {
      reponse.status = 404;
      reponse.statusText = 'Not Found';
    }

    inject(SeoService).definirPage({ titre: 'Page introuvable', indexable: false });
  }
}
