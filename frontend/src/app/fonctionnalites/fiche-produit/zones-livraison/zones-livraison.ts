import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';

import { ZoneLivraison } from '../../../noyau/api/modeles';
import { Icone } from '../../../partage/composants/icone/icone';
import { PrixFcfaPipe } from '../../../partage/pipes/prix-fcfa.pipe';

/**
 * Zones et tarifs de livraison (`/api/livraison/zones`), dans une fenêtre (`<dialog>` : Échap et le
 * geste retour d'Android la ferment). Code et données chargés seulement à la première demande.
 */
@Component({
  selector: 'bi-zones-livraison',
  imports: [Icone, PrixFcfaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog class="fenetre" #fenetre aria-labelledby="titre-zones">
      <div class="fenetre__entete">
        <h2 class="fenetre__titre" id="titre-zones">Livraison au Sénégal</h2>
        <button type="button" class="fenetre__fermer" (click)="fermer()" aria-label="Fermer">
          <bi-icone nom="fermer" />
        </button>
      </div>
      <div class="fenetre__corps">
        @if (zones(); as liste) {
          @if (liste.length) {
            <table class="zones">
              <thead>
                <tr>
                  <th scope="col">Zone</th>
                  <th scope="col" class="zones__nombre">Tarif</th>
                  <th scope="col" class="zones__nombre">Délai</th>
                </tr>
              </thead>
              <tbody>
                @for (zone of liste; track zone.nom) {
                  <tr>
                    <th scope="row">
                      {{ zone.nom }}
                      @if (zone.villesQuartiers.length) {
                        <span class="zones__quartiers">{{ zone.villesQuartiers.join(', ') }}</span>
                      }
                    </th>
                    <td class="zones__nombre">{{ zone.tarif | prixFcfa }}</td>
                    <td class="zones__nombre">{{ zone.delaiJours }} j</td>
                  </tr>
                }
              </tbody>
            </table>
            <p class="fenetre__note">Délais indicatifs en jours ouvrés, à partir de la confirmation de la commande.</p>
          } @else {
            <p>Les zones de livraison seront bientôt publiées : contactez-nous pour connaître le tarif.</p>
          }
        } @else if (erreur()) {
          <p>Impossible de charger les zones de livraison. Réessayez dans un instant.</p>
        } @else {
          <p aria-busy="true">Chargement des zones…</p>
        }
      </div>
    </dialog>
  `,
  styles: `
    .fenetre {
      width: min(100% - 2 * var(--espace-4), 36rem);
      max-height: min(100dvh - 2 * var(--espace-4), 40rem);
      /* Centrée (la réinitialisation globale retire la marge automatique du <dialog>) */
      margin: auto;
      padding: 0;
      color: var(--couleur-texte);
      background: var(--couleur-surface);
      border: 0;
      border-radius: var(--rayon-2);
      &[open] {
        display: flex;
        flex-direction: column;
      }
      &::backdrop {
        background: var(--couleur-voile);
      }
    }
    .fenetre__entete {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--espace-4);
      padding: var(--espace-2) var(--espace-2) var(--espace-2) var(--espace-4);
      border-block-end: var(--epaisseur-bordure) solid var(--couleur-bordure);
    }
    .fenetre__titre {
      margin: 0;
      font-size: var(--taille-lg);
    }
    .fenetre__fermer {
      display: grid;
      place-items: center;
      width: var(--cible-tactile);
      height: var(--cible-tactile);
      color: var(--couleur-texte);
      background: none;
      border: 0;
      cursor: pointer;
    }
    .fenetre__corps {
      overflow-y: auto;
      padding: var(--espace-4);
    }
    .fenetre__note {
      margin: var(--espace-3) 0 0;
      font-size: var(--taille-sm);
      color: var(--couleur-texte-secondaire);
    }
    .zones {
      width: 100%;
      border-collapse: collapse;
      font-size: var(--taille-sm);
      th,
      td {
        padding: var(--espace-2) var(--espace-2) var(--espace-2) 0;
        text-align: start;
        vertical-align: top;
        border-block-end: var(--epaisseur-bordure) solid var(--couleur-bordure);
      }
      thead th {
        font-size: var(--taille-xs);
        font-weight: var(--graisse-semi-gras);
        color: var(--couleur-texte-secondaire);
        text-transform: uppercase;
      }
      tbody th {
        font-weight: var(--graisse-semi-gras);
      }
    }
    .zones__nombre {
      font-family: var(--police-mono);
      text-align: end !important;
      white-space: nowrap;
    }
    .zones__quartiers {
      display: block;
      font-weight: var(--graisse-normale);
      color: var(--couleur-texte-secondaire);
    }
  `,
})
export class ZonesLivraison {
  private readonly http = inject(HttpClient);

  /** Compteur des demandes d'ouverture : chaque incrément ouvre la fenêtre. */
  readonly demandes = input(0);

  private readonly fenetre = viewChild.required<ElementRef<HTMLDialogElement>>('fenetre');
  protected readonly zones = signal<ZoneLivraison[] | null>(null);
  protected readonly erreur = signal(false);
  private demandesTraitees = 0;

  constructor() {
    afterRenderEffect(() => {
      const demandes = this.demandes();
      if (demandes > this.demandesTraitees) {
        this.demandesTraitees = demandes;
        untracked(() => this.ouvrir());
      }
    });
  }

  private ouvrir(): void {
    this.fenetre().nativeElement.showModal();
    if (this.zones() === null) {
      this.erreur.set(false);
      this.http.get<ZoneLivraison[]>('/api/livraison/zones').subscribe({
        next: (zones) => this.zones.set(zones),
        error: () => this.erreur.set(true),
      });
    }
  }

  protected fermer(): void {
    this.fenetre().nativeElement.close();
  }
}
