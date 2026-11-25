import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { ParametresSiteService, lienWhatsApp } from '../../../noyau/donnees/parametres-site.service';
import { OrigineWhatsApp, SuiviService } from '../../../noyau/suivi/suivi.service';
import { Icone } from '../../../partage/composants/icone/icone';

/**
 * « Vous ne trouvez pas ce que vous cherchez ? » : quand une liste est vide ou qu'une recherche ne donne
 * rien, le client peut demander le produit sur WhatsApp, avec un message prérempli (sa recherche, ou la
 * catégorie et ses filtres). Masqué si aucun numéro WhatsApp n'est configuré.
 */
@Component({
  selector: 'bi-demande-whatsapp',
  imports: [Icone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (lien(); as lien) {
      <aside class="demande">
        <p class="demande__titre">Vous ne trouvez pas ce que vous cherchez ?</p>
        <p class="demande__texte">
          Nous pouvons souvent le commander pour vous. Envoyez-nous votre demande, nous vous répondons avec un
          prix et un délai.
        </p>
        <a class="demande__bouton" [href]="lien" target="_blank" rel="noopener" (click)="suivi.contactWhatsApp(origine())">
          <bi-icone nom="message" taille="1.25rem" />
          Demandez-nous sur WhatsApp
        </a>
      </aside>
    }
  `,
  styles: `
    .demande {
      display: grid;
      gap: var(--espace-2);
      justify-items: start;
      padding: var(--espace-5);
      border: var(--epaisseur-bordure) solid var(--couleur-bordure);
      border-inline-start: 4px solid var(--couleur-whatsapp);
      border-radius: var(--rayon-2);
      background: var(--couleur-surface);
    }

    .demande__titre {
      margin: 0;
      font-family: var(--police-titre);
      font-size: var(--taille-lg);
      font-weight: var(--graisse-semi-gras);
    }

    .demande__texte {
      margin: 0;
      max-width: 40rem;
      color: var(--couleur-texte-secondaire);
    }

    .demande__bouton {
      display: inline-flex;
      align-items: center;
      gap: var(--espace-2);
      min-height: var(--cible-tactile);
      margin-block-start: var(--espace-2);
      padding-inline: var(--espace-5);
      border-radius: var(--rayon-2);
      background: var(--couleur-whatsapp);
      color: var(--couleur-sur-whatsapp);
      font-weight: var(--graisse-semi-gras);
      text-decoration: none;

      &:hover {
        background: var(--couleur-whatsapp-survol);
        color: var(--couleur-sur-whatsapp);
      }
    }
  `,
})
export class DemandeWhatsapp {
  private readonly site = inject(ParametresSiteService);
  protected readonly suivi = inject(SuiviService);

  readonly message = input.required<string>();
  readonly origine = input.required<OrigineWhatsApp>();

  protected readonly lien = computed(() => lienWhatsApp(this.site.parametres()?.whatsapp, this.message()));
}
