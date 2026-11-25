import { NgOptimizedImage } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';

import { Suggestions } from '../../noyau/api/modeles';
import { SuiviService } from '../../noyau/suivi/suivi.service';
import { Icone } from '../../partage/composants/icone/icone';
import { PrixFcfaPipe } from '../../partage/pipes/prix-fcfa.pipe';

/** Mêmes bornes que l'API (MoteurRecherche.LONGUEUR_MIN / LONGUEUR_MAX). */
const LONGUEUR_MIN = 2;
const LONGUEUR_MAX = 100;
/** Attente après la dernière frappe : évite une requête par lettre sur un forfait data limité. */
const ATTENTE_MS = 250;

interface Option {
  id: string;
  lien: string[];
  parametres?: Record<string, string | number>;
  libelle: string;
}

/**
 * Recherche avec autocomplétion (`/api/recherche/suggestions`) : produits, catégories, marques.
 * Motif ARIA « combobox » : flèches haut/bas, Entrée, Échap ; la requête précédente est annulée à
 * chaque frappe. La page de résultats (`/recherche?q=`) arrive en partie 3b.
 */
@Component({
  selector: 'bi-barre-recherche',
  imports: [Icone, NgOptimizedImage, PrixFcfaPipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(focusout)': 'surSortieFocus($event)' },
  templateUrl: './barre-recherche.html',
  styleUrl: './barre-recherche.scss',
})
export class BarreRecherche {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly suivi = inject(SuiviService);
  private readonly hote = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Identifiant unique : la barre existe en version ordinateur et mobile. */
  readonly ident = input.required<string>();

  protected readonly longueurMax = LONGUEUR_MAX;
  protected readonly terme = signal('');
  protected readonly ouvert = signal(false);
  protected readonly indexActif = signal(-1);

  private readonly reponse = toSignal(
    toObservable(this.terme).pipe(
      map((t) => t.trim()),
      debounceTime(ATTENTE_MS),
      distinctUntilChanged(),
      switchMap((q) =>
        q.length < LONGUEUR_MIN
          ? of(null)
          : this.http
              .get<Suggestions>('/api/recherche/suggestions', { params: { q } })
              .pipe(catchError(() => of(null))),
      ),
    ),
    { initialValue: null },
  );

  protected readonly suggestions = computed(() =>
    this.terme().trim().length >= LONGUEUR_MIN ? this.reponse() : null,
  );

  /** Options dans l'ordre de navigation au clavier ; la dernière lance la recherche complète. */
  protected readonly options = computed<Option[]>(() => {
    const s = this.suggestions();
    if (!s) {
      return [];
    }
    const base = this.ident();
    return [
      ...s.produits.map((p, i) => ({
        id: `${base}-produit-${i}`,
        lien: ['/produit', p.slug],
        parametres: { variante: p.varianteId },
        libelle: p.nom,
      })),
      ...s.categories.map((c, i) => ({ id: `${base}-categorie-${i}`, lien: ['/categorie', c.slug], libelle: c.nom })),
      ...s.marques.map((m, i) => ({ id: `${base}-marque-${i}`, lien: ['/marque', m.slug], libelle: m.nom })),
      {
        id: `${base}-tout`,
        lien: ['/recherche'],
        parametres: { q: this.terme().trim() },
        libelle: `Tous les résultats pour « ${this.terme().trim()} »`,
      },
    ];
  });

  protected readonly panneauVisible = computed(() => this.ouvert() && this.suggestions() !== null);

  protected readonly idActif = computed(() => this.options()[this.indexActif()]?.id ?? null);

  protected readonly aucunResultat = computed(() => {
    const s = this.suggestions();
    return !!s && s.produits.length + s.categories.length + s.marques.length === 0;
  });

  protected saisir(valeur: string): void {
    this.terme.set(valeur);
    this.indexActif.set(-1);
    this.ouvert.set(true);
  }

  protected surTouche(evenement: KeyboardEvent): void {
    const nombre = this.options().length;
    switch (evenement.key) {
      case 'ArrowDown':
        if (nombre) {
          evenement.preventDefault();
          this.ouvert.set(true);
          this.indexActif.update((i) => (i + 1) % nombre);
        }
        break;
      case 'ArrowUp':
        if (nombre) {
          evenement.preventDefault();
          this.indexActif.update((i) => (i <= 0 ? nombre - 1 : i - 1));
        }
        break;
      case 'Escape':
        if (this.ouvert() && this.suggestions()) {
          this.fermer();
        } else {
          this.terme.set('');
        }
        break;
      case 'Enter': {
        const option = this.options()[this.indexActif()];
        if (option && this.panneauVisible()) {
          evenement.preventDefault();
          this.ouvrirOption(option);
        }
        break;
      }
    }
  }

  protected valider(evenement: Event): void {
    evenement.preventDefault();
    const q = this.terme().trim();
    if (q.length < LONGUEUR_MIN) {
      return;
    }
    this.suivi.rechercher(q);
    this.fermer();
    void this.router.navigate(['/recherche'], { queryParams: { q } });
  }

  protected ouvrirOption(option: Option): void {
    if (option.id.endsWith('-tout')) {
      this.suivi.rechercher(this.terme().trim());
    }
    this.fermer();
    void this.router.navigate(option.lien, { queryParams: option.parametres });
  }

  protected surSortieFocus(evenement: FocusEvent): void {
    const cible = evenement.relatedTarget as Node | null;
    if (!cible || !this.hote.nativeElement.contains(cible)) {
      this.fermer();
    }
  }

  protected fermer(): void {
    this.ouvert.set(false);
    this.indexActif.set(-1);
  }
}
