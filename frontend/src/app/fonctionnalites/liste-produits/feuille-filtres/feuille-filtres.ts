import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';

import { Filtres } from '../../../noyau/api/modeles';
import { Icone } from '../../../partage/composants/icone/icone';
import { CodeTri, Criteres, nombreFiltres, sansFiltres } from '../criteres';
import { PanneauFiltres } from '../panneau-filtres/panneau-filtres';
import { SourceListe } from '../source-liste';

/** Délai avant de recompter les produits pendant les choix (plusieurs cases cochées d'affilée). */
const DELAI_RECOMPTE_MS = 250;

/**
 * Feuille plein écran « Filtrer et trier » (téléphones et tablettes). Les choix restent un brouillon :
 * le nombre de produits correspondants est recompté en direct, et rien n'est appliqué avant le bouton
 * « Voir n produits » (une seule entrée d'historique remplacée, une seule nouvelle liste chargée).
 * `<dialog>` natif : focus piégé, fond inerte, Échap et geste retour d'Android ferment la feuille.
 */
@Component({
  selector: 'bi-feuille-filtres',
  imports: [Icone, PanneauFiltres],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './feuille-filtres.html',
  styleUrl: './feuille-filtres.scss',
})
export class FeuilleFiltres {
  readonly criteres = input.required<Criteres>();
  readonly filtres = input.required<Filtres | undefined>();
  readonly source = input.required<SourceListe>();
  /**
   * Compteur des demandes d'ouverture : chaque incrément ouvre la feuille. Le code de la feuille n'est
   * chargé qu'au premier toucher sur « Filtrer et trier » ; ce toucher l'ouvre dès qu'elle est prête.
   */
  readonly demandes = input(0);

  readonly appliquer = output<Criteres>();

  private readonly dialogue = viewChild.required<ElementRef<HTMLDialogElement>>('dialogue');

  protected readonly brouillon = signal<Criteres | null>(null);
  protected readonly filtresBrouillon = signal<Filtres | undefined>(undefined);
  protected readonly recompte = signal(false);
  protected readonly nombreActifs = computed(() => nombreFiltres(this.brouillon() ?? this.criteres()));
  protected readonly total = computed(() => this.filtresBrouillon()?.totalProduits);

  private readonly aRecompter = new Subject<Criteres>();
  private demandesTraitees = 0;

  constructor() {
    afterRenderEffect(() => {
      const demandes = this.demandes();
      if (demandes > this.demandesTraitees) {
        this.demandesTraitees = demandes;
        untracked(() => this.ouvrir());
      }
    });
    const abonnement = this.aRecompter
      .pipe(
        debounceTime(DELAI_RECOMPTE_MS),
        switchMap((criteres) => this.source().filtresPour(criteres).pipe(catchError(() => of(undefined)))),
      )
      .subscribe((filtres) => {
        if (filtres) this.filtresBrouillon.set(filtres);
        this.recompte.set(false);
      });
    inject(DestroyRef).onDestroy(() => abonnement.unsubscribe());
  }

  private ouvrir(): void {
    this.brouillon.set(this.criteres());
    this.filtresBrouillon.set(this.filtres());
    this.recompte.set(false);
    this.dialogue().nativeElement.showModal();
  }

  protected fermer(): void {
    this.dialogue().nativeElement.close();
  }

  protected surFermeture(): void {
    this.brouillon.set(null);
  }

  protected modifier(criteres: Criteres): void {
    this.brouillon.set(criteres);
    this.recompte.set(true);
    this.aRecompter.next(criteres);
  }

  protected trier(code: CodeTri): void {
    // Le tri ne change pas le nombre de produits : pas de recompte
    this.brouillon.update((b) => b && { ...b, tri: code, page: 1 });
  }

  protected toutEffacer(): void {
    const brouillon = this.brouillon();
    if (brouillon) this.modifier(sansFiltres(brouillon));
  }

  protected valider(): void {
    const brouillon = this.brouillon();
    this.fermer();
    if (brouillon) this.appliquer.emit(brouillon);
  }

  protected triCourant(): CodeTri {
    return this.brouillon()?.tri ?? this.source().triParDefaut;
  }
}
