import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Composant racine. L'en-tête, le méga-menu et le pied de page arrivent en phase 3.
 */
@Component({
  selector: 'bi-racine',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="lien-evitement" href="#contenu">Aller au contenu</a>
    <main id="contenu" tabindex="-1">
      <router-outlet />
    </main>
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
export class App {}
