import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationStart, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';

import { CategorieNoeud } from '../../noyau/api/modeles';
import { CatalogueService } from '../../noyau/donnees/catalogue.service';
import { Icone } from '../../partage/composants/icone/icone';

/** Intention : la souris qui ne fait que traverser l'en-tête n'ouvre rien. */
export const DELAI_OUVERTURE_MS = 150;
/** Tolérance : un léger écart de la souris (vers le panneau) ne ferme pas le menu. */
export const DELAI_FERMETURE_MS = 250;
const MARQUES_AFFICHEES = 8;

/**
 * Méga-menu des catégories (ordinateur). Survol avec délai d'intention, clic et clavier (Entrée, Espace,
 * Échap) ; aucune fonction n'est réservée au survol : le tiroir mobile donne accès aux mêmes liens.
 */
@Component({
  selector: 'bi-mega-menu',
  imports: [Icone, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(pointerleave)': 'quitter($event)',
    '(pointerenter)': 'annulerFermeture()',
    '(focusout)': 'surSortieFocus($event)',
    '(keydown.escape)': 'fermerEtRevenir()',
  },
  templateUrl: './mega-menu.html',
  styleUrl: './mega-menu.scss',
})
export class MegaMenu {
  private readonly catalogue = inject(CatalogueService);
  private readonly hote = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly categories = this.catalogue.categories;
  protected readonly marques = computed(() => this.catalogue.marques().slice(0, MARQUES_AFFICHEES));
  /** Slug de la catégorie dont le panneau est ouvert. */
  protected readonly ouverte = signal<string | null>(null);

  private minuterie: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(Router)
      .events.pipe(
        filter((e) => e instanceof NavigationStart),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.fermer());
    inject(DestroyRef).onDestroy(() => clearTimeout(this.minuterie));
  }

  protected survoler(evenement: PointerEvent, categorie: CategorieNoeud): void {
    // Au toucher, le navigateur simule un survol : seul le clic compte
    if (evenement.pointerType !== 'mouse') {
      return;
    }
    clearTimeout(this.minuterie);
    if (this.ouverte() !== null) {
      // Un menu est déjà ouvert : passage immédiat d'une catégorie à l'autre
      this.ouverte.set(categorie.enfants.length ? categorie.slug : null);
      return;
    }
    if (categorie.enfants.length) {
      this.minuterie = setTimeout(() => this.ouverte.set(categorie.slug), DELAI_OUVERTURE_MS);
    }
  }

  protected basculer(categorie: CategorieNoeud): void {
    clearTimeout(this.minuterie);
    this.ouverte.update((slug) => (slug === categorie.slug ? null : categorie.slug));
  }

  protected quitter(evenement: PointerEvent): void {
    if (evenement.pointerType !== 'mouse') {
      return;
    }
    clearTimeout(this.minuterie);
    this.minuterie = setTimeout(() => this.ouverte.set(null), DELAI_FERMETURE_MS);
  }

  protected annulerFermeture(): void {
    if (this.ouverte() !== null) {
      clearTimeout(this.minuterie);
    }
  }

  protected surSortieFocus(evenement: FocusEvent): void {
    const cible = evenement.relatedTarget as Node | null;
    if (cible && !this.hote.nativeElement.contains(cible)) {
      this.fermer();
    }
  }

  protected fermerEtRevenir(): void {
    const slug = this.ouverte();
    if (slug === null) {
      return;
    }
    this.fermer();
    this.hote.nativeElement.querySelector<HTMLElement>(`[data-categorie="${slug}"]`)?.focus();
  }

  protected fermer(): void {
    clearTimeout(this.minuterie);
    this.ouverte.set(null);
  }
}
