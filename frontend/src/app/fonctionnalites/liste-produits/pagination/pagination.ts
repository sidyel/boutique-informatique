import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Params, RouterLink } from '@angular/router';

import { Icone } from '../../../partage/composants/icone/icone';

/** Pages affichées autour de la page courante : 1 … 4 5 6 … 20. `null` : points de suspension. */
export function pagesAffichees(page: number, total: number): (number | null)[] {
  const voisines = new Set([1, total, page - 1, page, page + 1].filter((n) => n >= 1 && n <= total));
  const triees = [...voisines].sort((a, b) => a - b);
  const resultat: (number | null)[] = [];
  triees.forEach((n, i) => {
    const precedente = triees[i - 1];
    if (precedente !== undefined && n - precedente === 2) resultat.push(n - 1);
    else if (precedente !== undefined && n - precedente > 2) resultat.push(null);
    resultat.push(n);
  });
  return resultat;
}

/**
 * Pagination par de vrais liens (`?page=2`) : Google les suit, et chaque page crée une entrée
 * d'historique (le retour arrière ramène à la page précédente). Cibles de 44 px au toucher.
 */
@Component({
  selector: 'bi-pagination',
  imports: [Icone, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (total() > 1) {
      <nav class="pagination" aria-label="Pagination">
        @if (page() > 1) {
          <a class="pagination__lien" [routerLink]="[]" [queryParams]="parametres()(page() - 1)" rel="prev">
            <bi-icone nom="chevron-gauche" taille="1rem" />
            <span class="visuellement-masque">Page précédente</span>
          </a>
        }
        @for (n of pages(); track $index) {
          @if (n === null) {
            <span class="pagination__ellipse" aria-hidden="true">…</span>
          } @else if (n === page()) {
            <span class="pagination__lien pagination__lien--courante" aria-current="page">{{ n }}</span>
          } @else {
            <a
              class="pagination__lien"
              [class.pagination__lien--voisine]="n !== 1 && n !== total()"
              [routerLink]="[]"
              [queryParams]="parametres()(n)"
              [attr.aria-label]="'Page ' + n"
            >
              {{ n }}
            </a>
          }
        }
        @if (page() < total()) {
          <a class="pagination__lien" [routerLink]="[]" [queryParams]="parametres()(page() + 1)" rel="next">
            <bi-icone nom="chevron-droite" taille="1rem" />
            <span class="visuellement-masque">Page suivante</span>
          </a>
        }
      </nav>
    }
  `,
  styles: `
    .pagination {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: var(--espace-1);
      margin-block: var(--espace-8);
    }

    .pagination__lien,
    .pagination__ellipse {
      display: inline-grid;
      place-items: center;
      min-width: var(--cible-tactile);
      height: var(--cible-tactile);
      padding-inline: var(--espace-2);
      font-family: var(--police-mono);
      font-size: var(--taille-sm);
    }

    .pagination__lien {
      border: var(--epaisseur-bordure) solid var(--couleur-bordure);
      border-radius: var(--rayon-1);
      background: var(--couleur-surface);
      color: var(--couleur-texte);
      text-decoration: none;

      &:hover {
        border-color: var(--couleur-texte);
      }
    }

    .pagination__lien--courante {
      border-color: var(--couleur-texte);
      background: var(--couleur-texte);
      color: var(--couleur-texte-inverse);
      font-weight: var(--graisse-semi-gras);
    }

    .pagination__ellipse {
      min-width: 1rem;
      padding-inline: 0;
      color: var(--couleur-texte-secondaire);
    }

    // Téléphones étroits : précédente, 1, courante, dernière, suivante (tout tient sur une ligne)
    @media (max-width: 26rem) {
      .pagination__lien--voisine {
        display: none;
      }
    }
  `,
})
export class Pagination {
  readonly page = input.required<number>();
  readonly total = input.required<number>();
  /** Paramètres d'URL de la page n (filtres et tri conservés). */
  readonly parametres = input.required<(page: number) => Params>();

  protected readonly pages = computed(() => pagesAffichees(this.page(), this.total()));
}
