import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { SessionService } from '../../../noyau/auth/session.service';
import { Adresse, CommandeResume, CompteService } from '../../../noyau/compte/compte.service';
import { PanierService } from '../../../noyau/panier/panier.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Squelette } from '../../../partage/composants/squelette/squelette';
import { PrixFcfaPipe } from '../../../partage/pipes/prix-fcfa.pipe';
import { LIBELLES_STATUT } from '../../commande/page-suivi/suivi-statuts';

/** Tableau de bord : identité, adresse par défaut, accès rapides. Les commandes arrivent en 5c. */
@Component({
  selector: 'bi-tableau-de-bord',
  imports: [Bouton, RouterLink, Squelette, PrixFcfaPipe],
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
        <!-- Hauteur réservée (carte d'adresse) : rien ne bouge quand la réponse arrive -->
        <div class="bloc-adresse">
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
        } @else {
          <bi-squelette hauteur="7.5rem" />
        }
        </div>
        <a routerLink="/compte/adresses">Gérer mes adresses</a>
      </section>

      <section class="espace__section" aria-labelledby="titre-commandes">
        <h2 id="titre-commandes">Dernière commande</h2>
        <div class="bloc-commandes">
          @if (commandesChargees()) {
            @for (c of dernieres(); track c.numero) {
              <a class="ligne-commande" [routerLink]="['/compte/commandes', c.numero]">
                <span class="ligne-commande__image" aria-hidden="true"></span>
                <span class="ligne-commande__corps">
                  <span class="ligne-commande__numero">{{ c.numero }}</span>
                  <span class="ligne-commande__meta">{{ c.total | prixFcfa }}</span>
                </span>
                <span class="ligne-commande__statut ligne-commande__statut--{{ c.statut }}">{{ libelles[c.statut] }}</span>
              </a>
            } @empty {
              <p class="espace__aide">Aucune commande pour le moment.</p>
            }
          } @else {
            <bi-squelette hauteur="6rem" />
          }
        </div>
        <a routerLink="/compte/commandes">Voir toutes mes commandes</a>
      </section>

      <button biBouton class="espace__deconnexion" variante="contour" type="button" (click)="deconnecter()">Se déconnecter</button>
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
  protected readonly dernieres = signal<CommandeResume[]>([]);
  protected readonly commandesChargees = signal(false);
  protected readonly libelles = LIBELLES_STATUT;

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Mon compte', indexable: false });
    void this.compte
      .adresses()
      .then((liste) => this.parDefaut.set(liste.find((a) => a.parDefaut) ?? null))
      .catch(() => this.parDefaut.set(null))
      .finally(() => this.chargee.set(true));
    void this.compte
      .commandes(0, 1)
      .then((page) => this.dernieres.set(page.content))
      .catch(() => this.dernieres.set([]))
      .finally(() => this.commandesChargees.set(true));
  }

  protected async deconnecter(): Promise<void> {
    await this.session.deconnecter();
    this.panier.reinitialiserLocalement();
    await this.router.navigateByUrl('/');
  }
}
