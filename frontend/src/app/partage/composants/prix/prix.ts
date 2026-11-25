import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { PrixFcfaPipe } from '../../pipes/prix-fcfa.pipe';

export type TaillePrix = 'sm' | 'md' | 'lg';

/**
 * Prix en FCFA, avec prix barré et pourcentage de réduction facultatifs.
 * Le prix barré n'est affiché que s'il est strictement supérieur au prix actuel.
 */
@Component({
  selector: 'bi-prix',
  imports: [PrixFcfaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'prix',
    '[class.prix--sm]': "taille() === 'sm'",
    '[class.prix--lg]': "taille() === 'lg'",
    '[class.prix--promo]': 'estEnPromotion()',
  },
  template: `
    <span class="prix__actuel">
      @if (estEnPromotion()) {
        <span class="visuellement-masque">Prix promotionnel : </span>
      }
      {{ montant() | prixFcfa }}
    </span>
    @if (estEnPromotion()) {
      <s class="prix__barre">
        <span class="visuellement-masque">Prix habituel : </span>{{ montantBarre() | prixFcfa }}
      </s>
      <span class="prix__reduction">−{{ pourcentageReduction() }} %</span>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-wrap: wrap;
      align-items: baseline;
      column-gap: var(--espace-2);
      row-gap: var(--espace-1);
      font-family: var(--police-prix);
      font-variant-numeric: tabular-nums;
    }

    .prix__actuel {
      font-family: var(--police-prix);
      font-size: var(--taille-xl);
      font-weight: var(--graisse-gras);
      line-height: var(--interligne-serre);
      color: var(--couleur-texte);
      white-space: nowrap;
    }

    :host(.prix--promo) .prix__actuel {
      color: var(--couleur-promo-fonce);
    }

    .prix__barre {
      font-size: var(--taille-sm);
      color: var(--couleur-texte-secondaire);
      white-space: nowrap;
    }

    .prix__reduction {
      padding: var(--espace-px) var(--espace-1);
      font-size: var(--taille-xs);
      font-weight: var(--graisse-gras);
      color: var(--couleur-texte-inverse);
      background: var(--couleur-promo);
      border-radius: var(--rayon-1);
      white-space: nowrap;
    }

    :host(.prix--sm) .prix__actuel {
      font-size: var(--taille-base);
    }

    :host(.prix--sm) .prix__barre {
      font-size: var(--taille-xs);
    }

    :host(.prix--lg) .prix__actuel {
      font-size: var(--taille-3xl);
    }

    :host(.prix--lg) .prix__barre {
      font-size: var(--taille-base);
    }
  `,
})
export class Prix {
  /** Prix actuel en FCFA (entier). */
  readonly montant = input.required<number>();
  /** Prix habituel en FCFA (facultatif). */
  readonly montantBarre = input<number | null | undefined>(null);
  readonly taille = input<TaillePrix>('md');

  readonly estEnPromotion = computed(() => {
    const barre = this.montantBarre();
    return barre != null && barre > this.montant();
  });

  /** Arrondi à l'entier inférieur : on n'annonce jamais une réduction plus forte que la réalité. */
  readonly pourcentageReduction = computed(() => {
    const barre = this.montantBarre();
    if (!this.estEnPromotion() || barre == null) {
      return 0;
    }
    return Math.floor(((barre - this.montant()) / barre) * 100);
  });
}
