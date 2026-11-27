import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';

/** Page affichée juste après la suppression du compte : plus de session, rien de personnel. */
@Component({
  selector: 'bi-compte-supprime',
  imports: [RouterLink, Bouton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .page-supprime {
      max-width: 36rem;
      padding-block: var(--espace-12);
    }
  `,
  template: `
    <div class="conteneur page-supprime">
      <h1>Votre compte est supprimé</h1>
      <p>
        Un e-mail de confirmation vous a été envoyé. Vos adresses, votre panier et vos connexions sont effacés ; vos
        factures sont conservées pendant la durée légale.
      </p>
      <a biBouton routerLink="/">Retourner sur la boutique</a>
    </div>
  `,
})
export class CompteSupprime {
  constructor() {
    inject(SeoService).definirPage({ titre: 'Compte supprimé', indexable: false });
  }
}
