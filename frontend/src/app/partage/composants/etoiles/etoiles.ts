import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Tracé de l'étoile Lucide (« star »), rempli. */
const ETOILE =
  'M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.12 2.12 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904' +
  'l-3.736 3.638a2.12 2.12 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.12 2.12 0 0 0-1.973 0' +
  'L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.12 2.12 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906' +
  'l5.165-.755a2.12 2.12 0 0 0 1.597-1.16z';

/**
 * Note en étoiles (sur 5), avec remplissage partiel (4,5 → quatre étoiles et demie). Lue par les lecteurs
 * d'écran comme « Note : 4,5 sur 5 ». Ne jamais l'afficher sans avis : le parent décide.
 */
@Component({
  selector: 'bi-etoiles',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'img', '[attr.aria-label]': 'libelle()', '[style.--taille]': 'taille()' },
  template: `
    <span class="rangee rangee--vide" aria-hidden="true">
      @for (i of cinq; track i) {
        <svg viewBox="0 0 24 24" focusable="false"><path [attr.d]="trace" /></svg>
      }
    </span>
    <span class="rangee rangee--pleine" aria-hidden="true" [style.width.%]="pourcentage()">
      @for (i of cinq; track i) {
        <svg viewBox="0 0 24 24" focusable="false"><path [attr.d]="trace" /></svg>
      }
    </span>
  `,
  styles: `
    :host {
      position: relative;
      display: inline-block;
      flex: none;
      line-height: 0;
    }
    .rangee {
      display: inline-flex;
      gap: var(--espace-px);
      white-space: nowrap;
    }
    .rangee--pleine {
      position: absolute;
      inset-block-start: 0;
      inset-inline-start: 0;
      overflow: hidden;
      color: var(--couleur-note);
    }
    .rangee--vide {
      color: var(--couleur-bordure-forte);
    }
    svg {
      flex: none;
      width: var(--taille, 1rem);
      height: var(--taille, 1rem);
      fill: currentColor;
    }
  `,
})
export class Etoiles {
  /** Note de 0 à 5 (décimale possible). */
  readonly note = input.required<number>();
  readonly taille = input<string | null>(null);

  protected readonly trace = ETOILE;
  protected readonly cinq = [0, 1, 2, 3, 4];
  protected readonly pourcentage = computed(() => (Math.min(Math.max(this.note(), 0), 5) / 5) * 100);
  protected readonly libelle = computed(
    () => `Note : ${this.note().toLocaleString('fr-FR', { maximumFractionDigits: 1 })} sur 5`,
  );
}
