import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

import { LignePanier as Ligne } from '../../../noyau/api/modeles';
import { PanierService } from '../../../noyau/panier/panier.service';
import { Icone } from '../../../partage/composants/icone/icone';
import { PrixFcfaPipe } from '../../../partage/pipes/prix-fcfa.pipe';

/**
 * Ligne du panier : photo, nom, variante, prix, sélecteur de quantité (boutons de 44 px), retrait.
 * Signale clairement un changement de prix, un article devenu indisponible ou une quantité devenue trop élevée.
 */
@Component({
  selector: 'bi-ligne-panier',
  imports: [Icone, NgOptimizedImage, PrixFcfaPipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let l = ligne();
    <article class="ligne" [class.ligne--indisponible]="l.probleme === 'INDISPONIBLE'">
      <a class="ligne__image" [routerLink]="['/produit', l.slugProduit]" tabindex="-1" aria-hidden="true"
        (click)="navigation.emit()">
        @if (l.image) {
          <img [ngSrc]="l.image.url" fill sizes="5rem" [alt]="''" />
        }
      </a>
      <div class="ligne__infos">
        <a class="ligne__nom" [routerLink]="['/produit', l.slugProduit]" (click)="navigation.emit()">
          {{ l.marque }} {{ l.nomProduit }}
        </a>
        <p class="ligne__variante">
          {{ l.libelleVariante }} <span class="ligne__sku">· {{ l.sku }}</span>
        </p>
        <p class="ligne__prix">
          <span class="ligne__unitaire">{{ l.prixUnitaire | prixFcfa }}</span>
          @if (l.quantite > 1) {
            <span class="ligne__detail">× {{ l.quantite }} = {{ l.totalLigne | prixFcfa }}</span>
          }
        </p>
        @if (l.disponibilite === 'STOCK_FAIBLE' && !l.probleme) {
          <p class="ligne__stock">Plus que quelques exemplaires</p>
        }
      </div>

      @if (l.prixPrecedent !== null && l.probleme !== 'INDISPONIBLE') {
        <p class="ligne__alerte" role="status">
          <bi-icone nom="alerte" taille="1rem" />
          <span>
            Le prix de cet article est passé de
            <strong>{{ l.prixPrecedent | prixFcfa }}</strong> à
            <strong>{{ l.prixUnitaire | prixFcfa }}</strong>.
          </span>
        </p>
      }
      @if (l.probleme === 'INDISPONIBLE') {
        <p class="ligne__alerte ligne__alerte--danger" role="status">
          <bi-icone nom="alerte" taille="1rem" />
          <span>Cet article n'est plus disponible : retirez-le pour commander.</span>
        </p>
      } @else if (l.probleme === 'QUANTITE_TROP_ELEVEE') {
        <p class="ligne__alerte ligne__alerte--danger" role="status">
          <bi-icone nom="alerte" taille="1rem" />
          <span>Vous pouvez en commander {{ l.quantiteMax }} au plus.</span>
          <button type="button" class="ligne__lien" [disabled]="panier.enCours()" (click)="ajuster()">
            Passer à {{ l.quantiteMax }}
          </button>
        </p>
      }

      <div class="ligne__actions">
        @if (l.probleme !== 'INDISPONIBLE') {
          <div class="quantite" role="group" [attr.aria-label]="'Quantité de ' + l.nomProduit">
            <button type="button" class="quantite__bouton" aria-label="Diminuer la quantité"
              [disabled]="panier.enCours() || l.quantite <= 1" (click)="changer(l.quantite - 1)">
              <bi-icone nom="moins" taille="1.125rem" />
            </button>
            <output class="quantite__valeur" aria-live="polite">{{ l.quantite }}</output>
            <button type="button" class="quantite__bouton" aria-label="Augmenter la quantité"
              [disabled]="panier.enCours() || !augmentable()" (click)="changer(l.quantite + 1)">
              <bi-icone nom="plus" taille="1.125rem" />
            </button>
          </div>
        }
        <button type="button" class="ligne__retirer" [disabled]="panier.enCours()" (click)="retirer()">
          <bi-icone nom="corbeille" taille="1.125rem" />
          <span>Retirer</span>
        </button>
      </div>
    </article>
  `,
  styleUrl: './ligne-panier.scss',
})
export class LignePanier {
  protected readonly panier = inject(PanierService);

  readonly ligne = input.required<Ligne>();
  /** Clic vers la fiche produit (le tiroir se ferme). */
  readonly navigation = output();

  protected readonly augmentable = computed(() => this.ligne().quantite < this.ligne().quantiteMax);

  protected changer(quantite: number): void {
    void this.panier.modifierQuantite(this.ligne().id, quantite);
  }

  protected ajuster(): void {
    this.changer(this.ligne().quantiteMax);
  }

  protected retirer(): void {
    void this.panier.supprimer(this.ligne().id);
  }
}
