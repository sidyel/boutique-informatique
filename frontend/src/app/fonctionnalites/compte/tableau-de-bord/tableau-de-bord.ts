import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { SessionService } from '../../../noyau/auth/session.service';
import { Adresse, CompteService } from '../../../noyau/compte/compte.service';
import { PanierService } from '../../../noyau/panier/panier.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';

/** Tableau de bord : identité, adresse par défaut, accès rapides. Les commandes arrivent en 5c. */
@Component({
  selector: 'bi-tableau-de-bord',
  imports: [Bouton, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../espace.scss',
  template: `
    @if (session.utilisateur(); as client) {
      <h1 class="espace__titre">Bonjour {{ client.prenom }}</h1>
      <p class="auth__intro">{{ client.email }}</p>
      @if (!client.emailVerifie) {
        <p class="auth__erreur" role="status">
          Votre adresse e-mail n’est pas encore vérifiée : ouvrez le lien reçu à l’inscription. Vos commandes passées sans
          compte avec cette adresse apparaîtront ensuite ici.
        </p>
      }

      <section class="espace__section" aria-labelledby="titre-adresse">
        <h2 id="titre-adresse">Adresse de livraison par défaut</h2>
        @if (chargee()) {
          @if (parDefaut(); as a) {
            <div class="carte-adresse carte-adresse--defaut">
              <h3>{{ a.libelle }}</h3>
              <p>{{ a.nomComplet }} · {{ a.telephone }}</p>
              <p>{{ a.quartier }}, {{ a.ville }}</p>
              @if (a.pointDeRepere) {
                <p>Repère : {{ a.pointDeRepere }}</p>
              }
            </div>
          } @else {
            <p class="espace__aide">Aucune adresse enregistrée : ajoutez-en une pour commander plus vite.</p>
          }
        }
        <a routerLink="/compte/adresses">Gérer mes adresses</a>
      </section>

      <section class="espace__section" aria-labelledby="titre-commandes">
        <h2 id="titre-commandes">Mes commandes</h2>
        <p class="espace__aide">
          Retrouvez ici vos commandes passées avec cette adresse e-mail. Pour une commande récente, utilisez le lien de suivi
          reçu par e-mail.
        </p>
      </section>

      <button biBouton variante="contour" type="button" (click)="deconnecter()">Se déconnecter</button>
    }
  `,
})
export class TableauDeBord implements OnInit {
  protected readonly session = inject(SessionService);
  private readonly compte = inject(CompteService);
  private readonly panier = inject(PanierService);
  private readonly router = inject(Router);
  private readonly seo = inject(SeoService);

  protected readonly parDefaut = signal<Adresse | null>(null);
  protected readonly chargee = signal(false);

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Mon compte', indexable: false });
    void this.compte
      .adresses()
      .then((liste) => this.parDefaut.set(liste.find((a) => a.parDefaut) ?? null))
      .catch(() => this.parDefaut.set(null))
      .finally(() => this.chargee.set(true));
  }

  protected async deconnecter(): Promise<void> {
    await this.session.deconnecter();
    this.panier.reinitialiserLocalement();
    await this.router.navigateByUrl('/');
  }
}
