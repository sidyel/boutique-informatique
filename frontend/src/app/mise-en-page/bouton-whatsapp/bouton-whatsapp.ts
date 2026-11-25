import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ParametresSiteService } from '../../noyau/donnees/parametres-site.service';
import { SuiviService } from '../../noyau/suivi/suivi.service';
import { Icone } from '../../partage/composants/icone/icone';

/**
 * Bouton WhatsApp flottant, à portée du pouce (bas droit), au-dessus de la barre de navigation des
 * téléphones (`safe-area-inset-bottom`). Lien `wa.me` : un toucher ouvre directement l'application.
 * Masqué tant qu'aucun numéro WhatsApp n'est saisi dans les paramètres.
 */
@Component({
  selector: 'bi-bouton-whatsapp',
  imports: [Icone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (site.lienWhatsApp(); as lien) {
      <a
        class="whatsapp"
        [href]="lien"
        target="_blank"
        rel="noopener"
        [attr.aria-label]="'Nous écrire sur WhatsApp au ' + site.whatsappAffiche()"
        (click)="suivi.contactWhatsApp('flottant')"
      >
        <bi-icone nom="message" taille="1.75rem" />
      </a>
    }
  `,
  styles: `
    .whatsapp {
      position: fixed;
      inset-inline-end: var(--espace-4);
      inset-block-end: calc(var(--espace-4) + env(safe-area-inset-bottom));
      z-index: var(--z-collant);
      display: grid;
      place-items: center;
      width: 3.5rem;
      height: 3.5rem;
      color: var(--couleur-sur-whatsapp);
      background: var(--couleur-whatsapp);
      border-radius: var(--rayon-rond);
      box-shadow: var(--ombre-2);

      &:hover {
        color: var(--couleur-sur-whatsapp);
        background: var(--couleur-whatsapp-survol);
      }
    }
  `,
})
export class BoutonWhatsapp {
  protected readonly site = inject(ParametresSiteService);
  protected readonly suivi = inject(SuiviService);
}
