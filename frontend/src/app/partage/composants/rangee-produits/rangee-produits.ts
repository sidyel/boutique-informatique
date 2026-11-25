import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  inject,
  input,
  viewChild,
} from '@angular/core';

import { ProduitResume } from '../../../noyau/api/modeles';
import { ListeSuivie, SuiviService } from '../../../noyau/suivi/suivi.service';
import { CarteProduit } from '../carte-produit/carte-produit';
import { Icone } from '../icone/icone';

/**
 * Rangée de cartes produit à défilement horizontal (accueil, produits similaires).
 * Le défilement reste dans la rangée (aucun débordement de la page) ; au doigt il suit des crans, à la souris
 * les boutons précédent/suivant font défiler d'une largeur visible. Liste de suivi : `view_item_list`.
 */
@Component({
  selector: 'bi-rangee-produits',
  imports: [CarteProduit, Icone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="rangee" [attr.aria-labelledby]="liste().id + '-titre'">
      <div class="rangee__entete">
        <h2 class="rangee__titre" [id]="liste().id + '-titre'">{{ titre() }}</h2>
        <div class="rangee__fleches">
          <button type="button" class="fleche" aria-label="Produits précédents" (click)="defiler(-1)">
            <bi-icone nom="chevron-gauche" taille="1rem" />
          </button>
          <button type="button" class="fleche" aria-label="Produits suivants" (click)="defiler(1)">
            <bi-icone nom="chevron-droite" taille="1rem" />
          </button>
        </div>
      </div>
      <ul #piste class="rangee__piste" role="list">
        @for (produit of produits(); track produit.id; let i = $index) {
          <li class="rangee__element">
            <bi-carte-produit [produit]="produit" [liste]="liste()" [position]="i" />
          </li>
        }
      </ul>
    </section>
  `,
  styleUrl: './rangee-produits.scss',
})
export class RangeeProduits {
  private readonly suivi = inject(SuiviService);

  readonly titre = input.required<string>();
  readonly produits = input.required<ProduitResume[]>();
  readonly liste = input.required<ListeSuivie>();

  private readonly piste = viewChild.required<ElementRef<HTMLElement>>('piste');

  constructor() {
    // Navigateur uniquement : la liste est signalée une fois par contenu affiché
    afterRenderEffect(() => this.suivi.voirListe(this.liste(), this.produits()));
  }

  protected defiler(sens: 1 | -1): void {
    const piste = this.piste().nativeElement;
    piste.scrollBy({ left: sens * piste.clientWidth * 0.9, behavior: 'smooth' });
  }
}
