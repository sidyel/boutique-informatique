import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';

import { SessionService } from '../../../noyau/auth/session.service';
import { PanierService } from '../../../noyau/panier/panier.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';

/** Espace client (coquille de la partie 5a : le tableau de bord complet arrive en 5b). */
@Component({
  selector: 'bi-tableau-de-bord',
  imports: [Bouton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../auth.scss',
  template: `
    <section class="conteneur auth">
      @if (session.utilisateur(); as client) {
        <h1 class="auth__titre">Bonjour {{ client.prenom }}</h1>
        <p class="auth__intro">{{ client.email }}</p>
        @if (!client.emailVerifie) {
          <p class="auth__erreur" role="status">Votre adresse e-mail n’est pas encore vérifiée : consultez l’e-mail reçu à l’inscription.</p>
        }
        <button biBouton variante="contour" type="button" (click)="deconnecter()">Se déconnecter</button>
      }
    </section>
  `,
})
export class TableauDeBord implements OnInit {
  protected readonly session = inject(SessionService);
  private readonly panier = inject(PanierService);
  private readonly router = inject(Router);
  private readonly seo = inject(SeoService);

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Mon compte', indexable: false });
  }

  protected async deconnecter(): Promise<void> {
    await this.session.deconnecter();
    this.panier.reinitialiserLocalement();
    await this.router.navigateByUrl('/');
  }
}
