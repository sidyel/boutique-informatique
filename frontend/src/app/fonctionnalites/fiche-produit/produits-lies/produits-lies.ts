import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap } from 'rxjs';

import { ProduitResume } from '../../../noyau/api/modeles';
import { RecemmentConsultesService } from '../../../noyau/donnees/recemment-consultes.service';
import { RangeeProduits } from '../../../partage/composants/rangee-produits/rangee-produits';

/** Produits similaires (`/api/produits/{slug}/similaires`), chargés quand la section arrive à l'écran. */
@Component({
  selector: 'bi-produits-similaires',
  imports: [RangeeProduits],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (produits()?.length) {
      <bi-rangee-produits titre="Produits similaires" [produits]="produits()!" [liste]="liste()" />
    }
  `,
})
export class ProduitsSimilaires {
  private readonly http = inject(HttpClient);

  readonly slug = input.required<string>();

  protected readonly produits = toSignal(
    toObservable(this.slug).pipe(
      switchMap((slug) =>
        this.http.get<ProduitResume[]>(`/api/produits/${slug}/similaires`).pipe(catchError(() => of([]))),
      ),
    ),
  );
  protected readonly liste = computed(() => ({ id: `similaires-${this.slug()}`, nom: 'Produits similaires' }));
}

/** Produits récemment consultés (navigateur uniquement), sans le produit affiché. */
@Component({
  selector: 'bi-produits-recents',
  imports: [RangeeProduits],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (produits().length) {
      <bi-rangee-produits titre="Récemment consultés" [produits]="produits()" [liste]="liste" />
    }
  `,
})
export class ProduitsRecents {
  private readonly recents = inject(RecemmentConsultesService);

  /** Produit affiché, à exclure. */
  readonly produitId = input.required<number>();

  protected readonly produits = computed(() =>
    this.recents
      .produits()
      .filter((p) => p.id !== this.produitId())
      .slice(0, 8),
  );
  protected readonly liste = { id: 'recemment-consultes', nom: 'Récemment consultés' };
}
