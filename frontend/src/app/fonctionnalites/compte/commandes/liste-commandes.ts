import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { lireErreur } from '../../../noyau/auth/erreurs-api';
import { CommandeResume, CompteService } from '../../../noyau/compte/compte.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Squelette } from '../../../partage/composants/squelette/squelette';
import { PrixFcfaPipe } from '../../../partage/pipes/prix-fcfa.pipe';
import { LIBELLES_STATUT } from '../../commande/page-suivi/suivi-statuts';

/** « Mes commandes » : la plus récente en premier, y compris celles passées sans compte puis rattachées. */
@Component({
  selector: 'bi-liste-commandes',
  imports: [RouterLink, Bouton, Squelette, PrixFcfaPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../espace.scss',
  template: `
    <h1 class="espace__titre">Mes commandes</h1>
    <p class="auth__intro">Les commandes passées sans compte avec votre adresse e-mail vérifiée s’affichent aussi ici.</p>

    @if (erreur()) {
      <p class="auth__erreur" role="alert">{{ erreur() }}</p>
    }
    @if (!chargee()) {
      <div aria-busy="true">
        @for (i of [1, 2, 3]; track i) {
          <bi-squelette forme="bloc" hauteur="5.5rem" />
          <div class="espace__ecart"></div>
        }
      </div>
    } @else {
      @for (c of commandes(); track c.numero) {
        <a class="ligne-commande" [routerLink]="['/compte/commandes', c.numero]">
          @if (c.imageUrl) {
            <img class="ligne-commande__image" [src]="c.imageUrl" alt="" width="56" height="56" loading="lazy" />
          } @else {
            <span class="ligne-commande__image" aria-hidden="true"></span>
          }
          <span class="ligne-commande__corps">
            <span class="ligne-commande__numero">{{ c.numero }}</span>
            <span class="ligne-commande__resume">
              {{ c.premierArticle }}@if (c.nombreArticles > 1) { et {{ c.nombreArticles - 1 }} autre{{ c.nombreArticles > 2 ? 's' : '' }} }
            </span>
            <span class="ligne-commande__meta">
              <time [attr.datetime]="c.dateCreation">{{ dateLisible(c.dateCreation) }}</time> · {{ c.total | prixFcfa }}
            </span>
          </span>
          <span class="ligne-commande__statut ligne-commande__statut--{{ c.statut }}">{{ libelles[c.statut] }}</span>
        </a>
      } @empty {
        @if (!erreur()) {
          <p class="espace__aide">Vous n’avez pas encore de commande.</p>
          <a biBouton routerLink="/">Voir les produits</a>
        }
      }
      @if (page() + 1 < totalPages()) {
        <button biBouton variante="contour" type="button" [chargement]="envoi()" (click)="plus()">Voir les commandes plus anciennes</button>
      }
    }
  `,
})
export class ListeCommandes implements OnInit {
  private readonly compte = inject(CompteService);
  private readonly seo = inject(SeoService);

  protected readonly libelles = LIBELLES_STATUT;
  protected readonly commandes = signal<CommandeResume[]>([]);
  protected readonly chargee = signal(false);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Mes commandes', indexable: false });
    void this.charger(0);
  }

  protected plus(): Promise<void> {
    return this.charger(this.page() + 1);
  }

  protected dateLisible(iso: string): string {
    return new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Africa/Dakar' });
  }

  private async charger(page: number): Promise<void> {
    this.envoi.set(true);
    try {
      const reponse = await this.compte.commandes(page);
      this.commandes.update((liste) => (page === 0 ? reponse.content : [...liste, ...reponse.content]));
      this.page.set(reponse.number);
      this.totalPages.set(reponse.totalPages);
    } catch (e) {
      this.erreur.set(lireErreur(e).message);
    } finally {
      this.envoi.set(false);
      this.chargee.set(true);
    }
  }
}
