import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  inject,
  untracked,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { articlesSuivis, valeurSuivie } from '../../../noyau/panier/articles-suivis';
import { PanierService } from '../../../noyau/panier/panier.service';
import { SuiviService } from '../../../noyau/suivi/suivi.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Icone } from '../../../partage/composants/icone/icone';
import { Squelette } from '../../../partage/composants/squelette/squelette';
import { LignePanier } from '../ligne-panier/ligne-panier';
import { ResumePanier } from '../resume-panier/resume-panier';

/**
 * Panier latéral, ouvert après un ajout ou depuis l'icône de l'en-tête. `<dialog>` modal : Échap et le geste
 * retour d'Android le ferment, le focus y reste. Plein écran sur mobile, à droite sur ordinateur. Les boutons
 * « Commander » et « Voir le panier » restent en bas, à portée du pouce.
 *
 * Chargé seulement à la première ouverture (`@defer` dans le cadre des pages publiques).
 */
@Component({
  selector: 'bi-tiroir-panier',
  imports: [Bouton, Icone, LignePanier, ResumePanier, RouterLink, Squelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog class="tiroir" #fenetre aria-labelledby="titre-tiroir-panier" (close)="panier.fermerTiroir()"
      (click)="clicFond($event)">
      <div class="tiroir__entete">
        <h2 class="tiroir__titre" id="titre-tiroir-panier">
          Mon panier
          @if (panier.nombreArticles(); as n) {
            <span class="tiroir__nombre">({{ n }})</span>
          }
        </h2>
        <button type="button" class="tiroir__fermer" (click)="fermer()" aria-label="Fermer le panier">
          <bi-icone nom="fermer" />
        </button>
      </div>

      <div class="tiroir__corps">
        @for (message of panier.panier()?.avertissements ?? []; track message) {
          <p class="tiroir__message" role="status">{{ message }}</p>
        }
        @if (panier.erreur(); as erreur) {
          <p class="tiroir__message tiroir__message--erreur" role="alert">{{ erreur }}</p>
        }

        @if (panier.panier(); as p) {
          @if (p.lignes.length) {
            @for (ligne of p.lignes; track ligne.id) {
              <bi-ligne-panier [ligne]="ligne" (navigation)="fermer()" />
            }
          } @else {
            <div class="tiroir__vide">
              <bi-icone nom="panier" taille="2.5rem" />
              <p>Votre panier est vide.</p>
              <a biBouton variante="contour" routerLink="/" (click)="fermer()">Continuer mes achats</a>
            </div>
          }
        } @else if (panier.chargement()) {
          @for (i of [1, 2]; track i) {
            <div class="tiroir__squelette">
              <bi-squelette forme="bloc" largeur="4.5rem" hauteur="4.5rem" />
              <div>
                <bi-squelette forme="ligne" largeur="80%" />
                <bi-squelette forme="ligne" largeur="40%" />
              </div>
            </div>
          }
        }
      </div>

      @if (panier.panier(); as p) {
        @if (p.lignes.length) {
          <div class="tiroir__pied">
            <bi-resume-panier [panier]="p" />
            <div class="tiroir__boutons">
              @if (p.commandable) {
                <a biBouton variante="principal" taille="lg" [pleineLargeur]="true" routerLink="/commande"
                  (click)="fermer()">Commander</a>
              } @else {
                <button biBouton variante="principal" taille="lg" [pleineLargeur]="true" type="button" disabled>
                  Commander
                </button>
              }
              <a biBouton variante="contour" [pleineLargeur]="true" routerLink="/panier" (click)="fermer()">
                Voir le panier et le code promo
              </a>
            </div>
          </div>
        }
      }
    </dialog>
  `,
  styleUrl: './tiroir-panier.scss',
})
export class TiroirPanier {
  protected readonly panier = inject(PanierService);
  private readonly suivi = inject(SuiviService);
  private readonly fenetre = viewChild.required<ElementRef<HTMLDialogElement>>('fenetre');

  constructor() {
    afterRenderEffect(() => {
      const ouvert = this.panier.tiroirOuvert();
      const dialogue = this.fenetre().nativeElement;
      untracked(() => {
        if (ouvert && !dialogue.open) {
          dialogue.showModal();
          void this.ouvert();
        } else if (!ouvert && dialogue.open) {
          dialogue.close();
        }
      });
    });
  }

  protected fermer(): void {
    this.panier.effacerMessages();
    this.panier.fermerTiroir();
  }

  /** Un clic sur le voile (hors du panneau) ferme le tiroir. */
  protected clicFond(evenement: MouseEvent): void {
    if (evenement.target === this.fenetre().nativeElement) this.fermer();
  }

  private async ouvert(): Promise<void> {
    // Juste après un ajout, le panier vient d'arriver ; depuis l'en-tête, PanierService.ouvrirTiroir le relit
    if (this.panier.panier() === null) await this.panier.charger();
    const p = this.panier.panier();
    if (p?.lignes.length) this.suivi.voirPanier(articlesSuivis(p), valeurSuivie(p));
  }
}
