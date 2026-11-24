import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type FormeSquelette = 'ligne' | 'bloc' | 'cercle';

/**
 * Espace réservé pendant le chargement. Il a toujours des dimensions fixes
 * pour que l'arrivée du contenu ne décale pas la page (CLS).
 *
 *   <bi-squelette forme="bloc" largeur="100%" hauteur="12rem" />
 *   <bi-squelette largeur="60%" />
 */
@Component({
  selector: 'bi-squelette',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'squelette',
    'aria-hidden': 'true',
    '[class]': "'squelette--' + forme()",
    '[style.width]': 'largeur()',
    '[style.height]': "hauteur() ?? (forme() === 'cercle' ? largeur() : null)",
  },
  template: '',
  styles: `
    :host {
      display: block;
      background: linear-gradient(
          90deg,
          var(--couleur-squelette) 0%,
          var(--couleur-squelette-reflet) 50%,
          var(--couleur-squelette) 100%
        )
        0 0 / 200% 100%;
      animation: reflet var(--duree-squelette) ease-in-out infinite;
    }

    :host(.squelette--ligne) {
      height: 1em;
      border-radius: var(--rayon-1);
    }

    :host(.squelette--bloc) {
      border-radius: var(--rayon-2);
    }

    :host(.squelette--cercle) {
      border-radius: var(--rayon-rond);
    }

    @keyframes reflet {
      from {
        background-position: 100% 0;
      }
      to {
        background-position: -100% 0;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      :host {
        animation: none;
      }
    }
  `,
})
export class Squelette {
  readonly forme = input<FormeSquelette>('ligne');
  readonly largeur = input<string>('100%');
  readonly hauteur = input<string | null>(null);
}
