import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { BoutonWhatsapp } from './bouton-whatsapp/bouton-whatsapp';
import { Entete } from './entete/entete';
import { PiedDePage } from './pied-de-page/pied-de-page';

/** Cadre des pages publiques : en-tête, contenu, pied de page, bouton WhatsApp. L'admin a le sien. */
@Component({
  selector: 'bi-mise-en-page-publique',
  imports: [BoutonWhatsapp, Entete, PiedDePage, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="lien-evitement" href="#contenu">Aller au contenu</a>
    <bi-entete />
    <main id="contenu" tabindex="-1">
      <router-outlet />
    </main>
    @defer (hydrate on viewport) {
      <bi-pied-de-page />
    }
    <bi-bouton-whatsapp />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 100dvh;
    }

    main {
      flex: 1;
      outline: none;
    }

    .lien-evitement {
      position: absolute;
      inset-inline-start: var(--espace-4);
      inset-block-start: calc(-1 * var(--espace-12));
      z-index: var(--z-superieur);
      padding: var(--espace-2) var(--espace-4);
      background: var(--couleur-sombre);
      color: var(--couleur-texte-inverse);
      border-radius: var(--rayon-2);

      &:focus {
        inset-block-start: var(--espace-4);
      }
    }
  `,
})
export class MiseEnPagePublique {}
