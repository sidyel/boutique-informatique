import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { Panier } from '../../../noyau/api/modeles';
import { PanierService } from '../../../noyau/panier/panier.service';
import { Icone } from '../../../partage/composants/icone/icone';
import { PrixFcfaPipe } from '../../../partage/pipes/prix-fcfa.pipe';

/**
 * Montants du panier, tels que calculés par le serveur : sous-total, remise, livraison (estimée si une zone
 * est choisie), total. Si des prix ont changé, propose de les accepter avant de commander.
 */
@Component({
  selector: 'bi-resume-panier',
  imports: [Icone, PrixFcfaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let p = panier();
    <dl class="resume">
      <div class="resume__ligne">
        <dt>Sous-total ({{ p.nombreArticles }} article{{ p.nombreArticles > 1 ? 's' : '' }})</dt>
        <dd>{{ p.sousTotal | prixFcfa }}</dd>
      </div>
      @if (p.remise > 0) {
        <div class="resume__ligne resume__ligne--remise">
          <dt>Remise {{ p.codePromo?.code }}</dt>
          <dd>−{{ p.remise | prixFcfa }}</dd>
        </div>
      }
      <div class="resume__ligne">
        <dt>Livraison</dt>
        <dd>
          @if (p.fraisLivraison === null) {
            <span class="resume__note">{{ p.livraisonOfferte ? 'Offerte' : 'Selon la zone' }}</span>
          } @else if (p.fraisLivraison === 0) {
            Offerte
          } @else {
            {{ p.fraisLivraison | prixFcfa }}
          }
        </dd>
      </div>
      <div class="resume__ligne resume__ligne--total">
        <dt>{{ p.fraisLivraison === null && !p.livraisonOfferte ? 'Total hors livraison' : 'Total' }}</dt>
        <dd>{{ p.total | prixFcfa }}</dd>
      </div>
    </dl>

    @if (p.prixModifies) {
      <div class="prix-modifies" role="alert">
        <p>
          <bi-icone nom="alerte" taille="1rem" />
          Le prix d'au moins un article a changé depuis que vous l'avez ajouté. Vérifiez le panier avant de
          commander.
        </p>
        <button type="button" class="prix-modifies__bouton" [disabled]="service.enCours()"
          (click)="service.accepterPrix()">
          J'accepte les nouveaux prix
        </button>
      </div>
    }
  `,
  styles: `
    .resume {
      display: grid;
      gap: var(--espace-2);
      margin: 0;
    }
    .resume__ligne {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      gap: var(--espace-4);
      dt {
        color: var(--couleur-texte-secondaire);
      }
      dd {
        margin: 0;
        font-family: var(--police-prix);
        font-weight: var(--graisse-semi-gras);
        white-space: nowrap;
      }
    }
    .resume__ligne--remise dd {
      color: var(--couleur-succes);
    }
    .resume__ligne--total {
      padding-block-start: var(--espace-2);
      border-block-start: var(--epaisseur-bordure) solid var(--couleur-bordure);
      dt {
        color: var(--couleur-texte);
        font-weight: var(--graisse-semi-gras);
      }
      dd {
        font-size: var(--taille-lg);
        font-weight: var(--graisse-gras);
      }
    }
    .resume__note {
      font-family: var(--police-texte);
      font-weight: var(--graisse-normale);
      color: var(--couleur-texte-secondaire);
    }
    .prix-modifies {
      margin-block-start: var(--espace-4);
      padding: var(--espace-3);
      font-size: var(--taille-sm);
      background: var(--couleur-alerte-fond);
      border-inline-start: var(--espace-1) solid var(--couleur-alerte);
      border-radius: var(--rayon-1);
      p {
        display: flex;
        gap: var(--espace-2);
        margin: 0 0 var(--espace-2);
      }
      bi-icone {
        margin-block-start: var(--espace-px);
        color: var(--couleur-alerte);
      }
    }
    .prix-modifies__bouton {
      width: 100%;
      min-height: var(--cible-tactile);
      font: inherit;
      font-weight: var(--graisse-semi-gras);
      color: var(--couleur-texte);
      background: var(--couleur-surface);
      border: var(--epaisseur-bordure) solid var(--couleur-bordure-forte);
      border-radius: var(--rayon-2);
      cursor: pointer;
      &:disabled {
        cursor: not-allowed;
        opacity: 0.5;
      }
      &:focus-visible {
        outline: none;
        box-shadow: var(--anneau-focus);
      }
    }
  `,
})
export class ResumePanier {
  protected readonly service = inject(PanierService);
  readonly panier = input.required<Panier>();
}
