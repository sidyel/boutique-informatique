import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { lireErreur } from '../../../noyau/auth/erreurs-api';
import { CompteService, MonAvis } from '../../../noyau/compte/compte.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Squelette } from '../../../partage/composants/squelette/squelette';

const LIBELLES_STATUT: Record<MonAvis['statut'], string> = {
  EN_ATTENTE: 'En cours de modération',
  APPROUVE: 'Publié',
  REJETE: 'Non publié',
};

/** « Mes avis » : tous les avis du client, publiés ou non. */
@Component({
  selector: 'bi-liste-avis',
  imports: [RouterLink, Squelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../espace.scss',
  template: `
    <h1 class="espace__titre">Mes avis</h1>
    @if (erreur()) {
      <p class="auth__erreur" role="alert">{{ erreur() }}</p>
    }
    @if (!chargee()) {
      <div aria-busy="true"><bi-squelette forme="bloc" hauteur="6rem" /></div>
    } @else {
      @for (a of avis(); track a.id) {
        <article class="avis-client">
          <h2 class="avis-client__produit"><a [routerLink]="['/produit', a.slugProduit]">{{ a.produit }}</a></h2>
          <p class="avis-client__note" [attr.aria-label]="'Note : ' + a.note + ' sur 5'">{{ a.note }}/5</p>
          @if (a.titre) {
            <p class="avis-client__titre">{{ a.titre }}</p>
          }
          @if (a.commentaire) {
            <p>{{ a.commentaire }}</p>
          }
          <p class="espace__aide">
            {{ libelles[a.statut] }}@if (a.achatVerifie) { · Achat vérifié }
          </p>
          @if (a.reponseBoutique) {
            <p class="avis-client__reponse"><strong>Réponse de la boutique :</strong> {{ a.reponseBoutique }}</p>
          }
        </article>
      } @empty {
        @if (!erreur()) {
          <p class="espace__aide">Aucun avis pour le moment. Les avis que vous donnerez sur vos achats apparaîtront ici.</p>
        }
      }
    }
  `,
})
export class ListeAvis implements OnInit {
  private readonly compte = inject(CompteService);
  private readonly seo = inject(SeoService);

  protected readonly libelles = LIBELLES_STATUT;
  protected readonly avis = signal<MonAvis[]>([]);
  protected readonly chargee = signal(false);
  protected readonly erreur = signal<string | null>(null);

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Mes avis', indexable: false });
    this.compte
      .avis()
      .then((liste) => this.avis.set(liste))
      .catch((e) => this.erreur.set(lireErreur(e).message))
      .finally(() => this.chargee.set(true));
  }
}
