import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { CARACTERES_MIN, OCTETS_MAX, evaluerMotDePasse } from '../../../noyau/auth/mot-de-passe';

/**
 * Règles du mot de passe, affichées avant la saisie (pas découvertes après une erreur) et cochées au fil de la frappe.
 * S'utilise dans un `<bi-champ>` : le contenu est projeté sous le champ.
 */
@Component({
  selector: 'bi-regles-mot-de-passe',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="regles" aria-label="Règles du mot de passe">
      <li [class.regles__ok]="etat().longueurOk">
        <span class="regles__etat">{{ etat().longueurOk ? 'Respectée :' : 'À respecter :' }}</span>
        au moins {{ min }} caractères
      </li>
      <li [class.regles__ok]="etat().octetsOk && !vide()">
        <span class="regles__etat">{{ etat().octetsOk && !vide() ? 'Respectée :' : 'À respecter :' }}</span>
        pas plus de {{ max }} octets (les accents et symboles comptent double)
      </li>
    </ul>
    <p class="regles__conseil">Pas besoin de majuscule ni de chiffre : une phrase facile à retenir est la plus sûre.</p>
  `,
  styles: `
    .regles {
      display: flex;
      flex-direction: column;
      gap: var(--espace-1);
      margin: 0;
      padding: 0;
      font-size: var(--taille-sm);
      color: var(--couleur-texte-secondaire);
      list-style: none;
    }

    .regles li {
      display: flex;
      gap: var(--espace-2);
      align-items: baseline;
    }

    // Puce creuse, pleine et verte quand la règle est respectée (le texte reste la source d'information)
    .regles li::before {
      flex: none;
      width: 0.625rem;
      height: 0.625rem;
      content: '';
      border: 2px solid var(--couleur-texte-secondaire);
      border-radius: var(--rayon-rond);
    }

    .regles__ok {
      color: var(--couleur-succes);
    }

    .regles__ok::before {
      background: var(--couleur-succes);
      border-color: var(--couleur-succes);
    }

    // Annoncé par les lecteurs d'écran, invisible à l'écran (la couleur de la puce suffit)
    .regles__etat {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
    }

    .regles__conseil {
      margin: var(--espace-1) 0 0;
      font-size: var(--taille-sm);
      color: var(--couleur-texte-secondaire);
    }
  `,
})
export class ReglesMotDePasse {
  readonly motDePasse = input('');
  protected readonly min = CARACTERES_MIN;
  protected readonly max = OCTETS_MAX;
  protected readonly etat = computed(() => evaluerMotDePasse(this.motDePasse()));
  protected readonly vide = computed(() => this.motDePasse() === '');
}
