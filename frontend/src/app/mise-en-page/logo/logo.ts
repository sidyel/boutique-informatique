import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ParametresSiteService } from '../../noyau/donnees/parametres-site.service';

/**
 * Logo du site, lien vers l'accueil.
 * - Logo envoyé dans l'admin (`ParametresSite.logoUrl`, ou `logoSombreUrl` sur fond sombre) : affiché par
 *   `<img src>` uniquement (un SVG ne s'exécute jamais ainsi : ADR 0005 §5).
 * - Sinon, logo texte provisoire : le nom en Space Grotesk, précédé d'un petit carré orange.
 */
@Component({
  selector: 'bi-logo',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/" class="logo" [class.logo--sombre]="surFondSombre()">
      @if (url(); as url) {
        <img [src]="url" [alt]="nom()" class="logo__image" height="32" decoding="async" />
      } @else {
        <span class="logo__repere" aria-hidden="true"></span>
        <span class="logo__nom">{{ nom() }}</span>
      }
    </a>
  `,
  styles: `
    .logo {
      display: inline-flex;
      align-items: center;
      gap: var(--espace-2);
      min-height: var(--cible-tactile);
      color: var(--couleur-texte);
      text-decoration: none;

      &:hover {
        color: var(--couleur-texte);
      }
    }

    .logo--sombre,
    .logo--sombre:hover {
      color: var(--couleur-texte-inverse);
    }

    .logo__image {
      display: block;
      width: auto;
      height: 2rem;
      max-width: 10rem;
      object-fit: contain;
    }

    /* Détail graphique : un carré d'accent, comme un témoin lumineux de façade de PC */
    .logo__repere {
      width: 0.625rem;
      height: 0.625rem;
      background: var(--couleur-accent-decor);
      border-radius: var(--rayon-1);
      transform: translateY(-0.15em);
    }

    .logo__nom {
      font-family: var(--police-titre);
      font-size: var(--taille-xl);
      font-weight: var(--graisse-gras);
      letter-spacing: var(--approche-titre);
      line-height: 1;
    }
  `,
})
export class Logo {
  private readonly site = inject(ParametresSiteService);

  readonly surFondSombre = input(false);

  protected readonly nom = this.site.nomSite;

  protected readonly url = computed(() => {
    const parametres = this.site.parametres();
    // Sur fond sombre, le logo normal pourrait être illisible : à défaut de logo sombre, logo texte
    return (this.surFondSombre() ? parametres?.logoSombreUrl : parametres?.logoUrl) ?? null;
  });
}
