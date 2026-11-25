import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { VarianteFiche } from '../../../noyau/api/modeles';
import { AxeVariante, OptionVariante, axesVariantes, messagePlusProche, optionsAxe } from '../fiche';

/** Choix demandé : la variante visée et, si la combinaison n'existe pas, le message expliquant le report. */
export interface ChoixVariante {
  variante: VarianteFiche;
  message: string | null;
}

/**
 * Choix de la variante, un groupe de boutons par axe (mémoire, stockage, couleur…).
 * - Combinaison inexistante : option grisée, mais utilisable ; elle mène à la variante la plus proche
 *   et un message l'explique.
 * - Combinaison existante en rupture : option normale, mention « Rupture » (prix et fiche affichés).
 */
@Component({
  selector: 'bi-choix-variante',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (groupe of groupes(); track groupe.axe.cle) {
      <fieldset class="axe">
        <legend class="axe__titre">
          {{ groupe.axe.libelle }} :
          <strong>{{ courante().attributs[groupe.axe.cle] }}</strong>
        </legend>
        <div class="axe__options">
          @for (option of groupe.options; track option.valeur) {
            <button
              type="button"
              class="option"
              [class.option--courante]="option.courante"
              [class.option--inexistante]="!option.exacte"
              [attr.aria-pressed]="option.courante"
              (click)="choisir(groupe.axe, option)"
            >
              {{ option.valeur }}
              @if (!option.exacte) {
                <span class="visuellement-masque"> (pas dans cette configuration : version la plus proche)</span>
              } @else if (option.rupture && !option.courante) {
                <span class="option__rupture">Rupture</span>
              }
            </button>
          }
        </div>
      </fieldset>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: var(--espace-4);
    }
    .axe {
      min-width: 0;
      margin: 0;
      padding: 0;
      border: 0;
    }
    .axe__titre {
      float: left;
      width: 100%;
      margin-block-end: var(--espace-2);
      padding: 0;
      font-size: var(--taille-sm);
      color: var(--couleur-texte-secondaire);
      strong {
        font-weight: var(--graisse-semi-gras);
        color: var(--couleur-texte);
      }
    }
    .axe__options {
      clear: both;
      display: flex;
      flex-wrap: wrap;
      gap: var(--espace-2);
    }
    .option {
      display: inline-flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-width: 4.5rem;
      min-height: var(--cible-tactile);
      padding: var(--espace-1) var(--espace-3);
      font: inherit;
      font-size: var(--taille-sm);
      line-height: var(--interligne-serre);
      color: var(--couleur-texte);
      background: var(--couleur-surface);
      border: var(--epaisseur-bordure) solid var(--couleur-bordure-champ);
      border-radius: var(--rayon-1);
      cursor: pointer;
      &:hover {
        border-color: var(--couleur-texte);
      }
    }
    .option--courante {
      border-color: var(--couleur-accent);
      box-shadow: inset 0 0 0 1px var(--couleur-accent);
      font-weight: var(--graisse-semi-gras);
    }
    .option--inexistante {
      color: var(--couleur-texte-desactive);
      background: var(--couleur-surface-alt);
      border-style: dashed;
    }
    .option__rupture {
      font-size: var(--taille-2xs);
      color: var(--couleur-texte-secondaire);
    }
  `,
})
export class ChoixVarianteFiche {
  readonly variantes = input.required<VarianteFiche[]>();
  readonly courante = input.required<VarianteFiche>();

  readonly choisi = output<ChoixVariante>();

  private readonly axes = computed(() => axesVariantes(this.variantes()));

  protected readonly groupes = computed(() =>
    this.axes().map((axe) => ({ axe, options: optionsAxe(this.variantes(), this.courante(), axe) })),
  );

  protected choisir(axe: AxeVariante, option: OptionVariante): void {
    if (option.variante.id === this.courante().id) return;
    this.choisi.emit({
      variante: option.variante,
      message: option.exacte ? null : messagePlusProche(axe, option, this.courante(), this.axes()),
    });
  }
}
