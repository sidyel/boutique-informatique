import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface EtapeAriane {
  nom: string;
  /** Absent pour la page courante (dernière étape, sans lien). */
  lien?: string;
}

/**
 * Fil d'Ariane visible (cahier des charges, SEO). Sur téléphone, il défile horizontalement au lieu de
 * passer sur plusieurs lignes. Les données structurées BreadcrumbList viendront en phase 3d.
 */
@Component({
  selector: 'bi-fil-ariane',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="ariane" aria-label="Fil d'Ariane">
      <ol class="ariane__liste">
        <li><a routerLink="/">Accueil</a></li>
        @for (etape of etapes(); track $index) {
          <li>
            @if (etape.lien) {
              <a [routerLink]="etape.lien">{{ etape.nom }}</a>
            } @else {
              <span aria-current="page">{{ etape.nom }}</span>
            }
          </li>
        }
      </ol>
    </nav>
  `,
  styles: `
    .ariane {
      overflow-x: auto;
      scrollbar-width: none;
    }

    .ariane__liste {
      display: flex;
      align-items: center;
      margin: 0;
      padding: 0;
      list-style: none;
      font-size: var(--taille-sm);
      white-space: nowrap;
      color: var(--couleur-texte-secondaire);

      li + li::before {
        content: '/';
        padding-inline: var(--espace-2);
        color: var(--couleur-bordure-forte);
      }

      a {
        display: inline-flex;
        align-items: center;
        min-height: var(--cible-tactile);
        color: var(--couleur-texte-secondaire);
        text-decoration: none;

        &:hover {
          color: var(--couleur-accent);
          text-decoration: underline;
        }
      }

      [aria-current] {
        color: var(--couleur-texte);
      }
    }
  `,
})
export class FilAriane {
  readonly etapes = input.required<EtapeAriane[]>();
}
