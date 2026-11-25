import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';

import { Avis, Page } from '../../../noyau/api/modeles';
import { Etoiles } from '../../../partage/composants/etoiles/etoiles';
import { Icone } from '../../../partage/composants/icone/icone';

const MOIS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];

/**
 * « 12 nov. 2026 », identique côté serveur et dans le navigateur (aucune différence à l'hydratation :
 * pas d'Intl, date lue en UTC).
 */
export function dateAvis(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '' : `${date.getUTCDate()} ${MOIS[date.getUTCMonth()]} ${date.getUTCFullYear()}`;
}

/** « 4,5 » */
export function noteAffichee(note: number): string {
  return note.toFixed(1).replace('.', ',').replace(',0', '');
}

/**
 * Avis clients de la fiche : note moyenne, nombre d'avis, les 5 derniers (rendus par le serveur), puis
 * « Voir plus d'avis » par pages de 5. Sans avis : aucune étoile affichée.
 */
@Component({
  selector: 'bi-avis-produit',
  imports: [Etoiles, Icone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './avis-produit.html',
  styleUrl: './avis-produit.scss',
})
export class AvisProduit {
  private readonly http = inject(HttpClient);

  readonly slug = input.required<string>();
  readonly noteMoyenne = input<number | null>(null);
  readonly nombreAvis = input(0);
  /** Première page, chargée avec la fiche. */
  readonly premiers = input<Page<Avis> | undefined>(undefined);

  private readonly suivants = signal<Avis[]>([]);
  private readonly dernierePage = signal(1);
  protected readonly chargement = signal(false);
  protected readonly erreur = signal(false);

  protected readonly avis = computed(() => [...(this.premiers()?.elements ?? []), ...this.suivants()]);
  protected readonly reste = computed(() => Math.max(0, (this.premiers()?.totalElements ?? 0) - this.avis().length));
  protected readonly dateAvis = dateAvis;
  protected readonly noteAffichee = noteAffichee;

  protected voirPlus(): void {
    const page = this.premiers();
    if (!page || this.chargement()) return;
    const suivante = this.dernierePage() + 1;
    this.chargement.set(true);
    this.erreur.set(false);
    this.http
      .get<Page<Avis>>(`/api/produits/${this.slug()}/avis?page=${suivante}&taille=${page.taille}`)
      .subscribe({
        next: (resultat) => {
          const deja = new Set(this.avis().map((a) => a.id));
          this.suivants.update((liste) => [...liste, ...resultat.elements.filter((a) => !deja.has(a.id))]);
          this.dernierePage.set(suivante);
          this.chargement.set(false);
        },
        error: () => {
          this.erreur.set(true);
          this.chargement.set(false);
        },
      });
  }
}
