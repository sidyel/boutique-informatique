import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Composant racine. Les pages publiques s'affichent dans MiseEnPagePublique (en-tête, pied de page),
 * l'administration dans son propre cadre.
 */
@Component({
  selector: 'bi-racine',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<router-outlet />`,
})
export class App {}
