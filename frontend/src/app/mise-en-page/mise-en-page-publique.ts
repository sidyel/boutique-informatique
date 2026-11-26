import { ChangeDetectionStrategy, Component, afterNextRender, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { TiroirPanier } from '../fonctionnalites/panier/tiroir-panier/tiroir-panier';
import { PanierService } from '../noyau/panier/panier.service';
import { BoutonWhatsapp } from './bouton-whatsapp/bouton-whatsapp';
import { Entete } from './entete/entete';
import { PiedDePage } from './pied-de-page/pied-de-page';

/** Cadre des pages publiques : en-tête, contenu, pied de page, bouton WhatsApp. L'admin a le sien. */
@Component({
  selector: 'bi-mise-en-page-publique',
  imports: [BoutonWhatsapp, Entete, PiedDePage, RouterOutlet, TiroirPanier],
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
    <!-- Panier latéral : code chargé à la première ouverture seulement -->
    @defer (when panier.tiroirOuvert()) {
      <bi-tiroir-panier />
    }
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
export class MiseEnPagePublique {
  protected readonly panier = inject(PanierService);

  constructor() {
    // Compteur de l'en-tête : panier chargé dans le navigateur seulement, s'il en existe un
    afterNextRender(() => this.panier.initialiser());
  }
}
