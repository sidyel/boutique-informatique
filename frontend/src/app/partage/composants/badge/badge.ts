import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type TypeBadge =
  | 'neuf'
  | 'reconditionne'
  | 'occasion'
  | 'promo'
  | 'stock'
  | 'stock-faible'
  | 'rupture'
  | 'info'
  | 'neutre';

/**
 * Étiquette courte : état du produit, disponibilité, promotion.
 *
 *   <bi-badge type="stock">En stock</bi-badge>
 *   <bi-badge type="reconditionne">Reconditionné</bi-badge>
 */
@Component({
  selector: 'bi-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'badge',
    '[class]': "'badge--' + type()",
  },
  template: `<ng-content />`,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: var(--espace-1);
      padding: var(--espace-px) var(--espace-2);
      font-size: var(--taille-xs);
      font-weight: var(--graisse-semi-gras);
      line-height: 1.5;
      white-space: nowrap;
      border: var(--epaisseur-bordure) solid transparent;
      border-radius: var(--rayon-1);
    }

    :host(.badge--neuf),
    :host(.badge--neutre) {
      color: var(--couleur-texte);
      background: var(--couleur-surface-alt);
      border-color: var(--couleur-bordure);
    }

    :host(.badge--reconditionne),
    :host(.badge--info) {
      color: var(--couleur-info);
      background: var(--couleur-info-fond);
    }

    :host(.badge--occasion) {
      color: var(--couleur-texte-secondaire);
      background: var(--couleur-surface);
      border-color: var(--couleur-bordure-forte);
    }

    :host(.badge--promo) {
      color: var(--couleur-texte-inverse);
      background: var(--couleur-promo);
    }

    :host(.badge--stock) {
      color: var(--couleur-succes);
      background: var(--couleur-succes-fond);
    }

    :host(.badge--stock-faible) {
      color: var(--couleur-alerte);
      background: var(--couleur-alerte-fond);
    }

    :host(.badge--rupture) {
      color: var(--couleur-promo-fonce);
      background: var(--couleur-promo-fond);
    }

    /* Pastille devant les badges de disponibilité */
    :host(.badge--stock)::before,
    :host(.badge--stock-faible)::before,
    :host(.badge--rupture)::before {
      content: '';
      width: 0.5em;
      height: 0.5em;
      background: currentColor;
      border-radius: var(--rayon-rond);
    }
  `,
})
export class Badge {
  readonly type = input<TypeBadge>('neutre');
}
