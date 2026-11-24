import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  booleanAttribute,
  inject,
  input,
} from '@angular/core';

export type VarianteBouton = 'principal' | 'secondaire' | 'contour' | 'fantome' | 'danger';
export type TailleBouton = 'sm' | 'md' | 'lg';

/**
 * Bouton du système de design, appliqué sur un vrai `<button>` ou un vrai lien `<a>`
 * (sémantique et accessibilité natives conservées) :
 *
 *   <button biBouton type="submit" [chargement]="envoi()">Valider la commande</button>
 *   <a biBouton variante="contour" routerLink="/panier">Voir le panier</a>
 *
 * Pendant le chargement, les clics sont bloqués (pas de double commande) et `aria-busy` est posé.
 */
@Component({
  selector: 'button[biBouton], a[biBouton]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'bouton',
    '[class]': "'bouton--' + variante() + ' bouton--' + taille()",
    '[class.bouton--pleine-largeur]': 'pleineLargeur()',
    '[class.bouton--chargement]': 'chargement()',
    '[attr.aria-busy]': 'chargement() || null',
    '[attr.aria-disabled]': 'chargement() || null',
  },
  template: `
    @if (chargement()) {
      <span class="bouton__indicateur" aria-hidden="true"></span>
    }
    <span class="bouton__contenu"><ng-content /></span>
  `,
  styles: `
    :host {
      position: relative;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--espace-2);
      min-height: var(--hauteur-controle-md);
      padding-inline: var(--espace-5);
      border: var(--epaisseur-bordure) solid transparent;
      border-radius: var(--rayon-2);
      font-family: var(--police-texte);
      font-size: var(--taille-base);
      font-weight: var(--graisse-semi-gras);
      line-height: 1;
      text-decoration: none;
      white-space: nowrap;
      cursor: pointer;
      user-select: none;
      transition:
        background-color var(--duree-rapide) var(--courbe-standard),
        border-color var(--duree-rapide) var(--courbe-standard),
        color var(--duree-rapide) var(--courbe-standard);
    }

    :host(:focus-visible) {
      outline: none;
      box-shadow: var(--anneau-focus);
    }

    :host(:disabled),
    :host([aria-disabled='true']:not(.bouton--chargement)) {
      cursor: not-allowed;
      opacity: 0.5;
    }

    .bouton__contenu {
      display: inline-flex;
      align-items: center;
      gap: var(--espace-2);
    }

    /* --- Variantes ------------------------------------------------------ */
    :host(.bouton--principal) {
      background: var(--couleur-accent);
      color: var(--couleur-texte-inverse);
    }

    :host(.bouton--principal:hover:not(:disabled)) {
      background: var(--couleur-accent-survol);
      color: var(--couleur-texte-inverse);
    }

    :host(.bouton--secondaire) {
      background: var(--couleur-texte);
      color: var(--couleur-texte-inverse);
    }

    :host(.bouton--secondaire:hover:not(:disabled)) {
      background: var(--gris-700);
      color: var(--couleur-texte-inverse);
    }

    :host(.bouton--contour) {
      background: var(--couleur-surface);
      border-color: var(--couleur-bordure-champ);
      color: var(--couleur-texte);
    }

    :host(.bouton--contour:hover:not(:disabled)) {
      border-color: var(--couleur-texte);
      color: var(--couleur-texte);
    }

    :host(.bouton--fantome) {
      background: transparent;
      color: var(--couleur-texte);
    }

    :host(.bouton--fantome:hover:not(:disabled)) {
      background: var(--couleur-surface-alt);
      color: var(--couleur-texte);
    }

    :host(.bouton--danger) {
      background: var(--couleur-danger);
      color: var(--couleur-texte-inverse);
    }

    :host(.bouton--danger:hover:not(:disabled)) {
      background: var(--couleur-danger-survol);
      color: var(--couleur-texte-inverse);
    }

    /* --- Tailles -------------------------------------------------------- */
    :host(.bouton--sm) {
      min-height: var(--hauteur-controle-sm);
      padding-inline: var(--espace-3);
      font-size: var(--taille-sm);
    }

    :host(.bouton--lg) {
      min-height: var(--hauteur-controle-lg);
      padding-inline: var(--espace-6);
      font-size: var(--taille-lg);
    }

    :host(.bouton--pleine-largeur) {
      display: flex;
      width: 100%;
    }

    /* --- Chargement ----------------------------------------------------- */
    :host(.bouton--chargement) {
      cursor: progress;
    }

    :host(.bouton--chargement) .bouton__contenu {
      visibility: hidden;
    }

    .bouton__indicateur {
      position: absolute;
      width: 1.1em;
      height: 1.1em;
      border: 2px solid currentColor;
      border-right-color: transparent;
      border-radius: var(--rayon-rond);
      animation: rotation 0.7s linear infinite;
    }

    @keyframes rotation {
      to {
        transform: rotate(360deg);
      }
    }
  `,
})
export class Bouton {
  readonly variante = input<VarianteBouton>('principal');
  readonly taille = input<TailleBouton>('md');
  readonly chargement = input(false, { transform: booleanAttribute });
  readonly pleineLargeur = input(false, { transform: booleanAttribute });

  constructor() {
    // Écoute en phase de capture : elle passe avant les (click) posés par le parent sur
    // le même élément, ce qui permet de les bloquer pendant le chargement.
    const element: HTMLElement = inject(ElementRef).nativeElement;
    const bloquerSiChargement = (evenement: Event) => {
      if (this.chargement()) {
        evenement.preventDefault();
        evenement.stopImmediatePropagation();
      }
    };
    element.addEventListener('click', bloquerSiChargement, { capture: true });
    inject(DestroyRef).onDestroy(() =>
      element.removeEventListener('click', bloquerSiChargement, { capture: true }),
    );
  }
}
