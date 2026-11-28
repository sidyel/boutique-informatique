import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { lireErreur } from '../../noyau/auth/erreurs-api';
import { SessionService } from '../../noyau/auth/session.service';
import { SeoService } from '../../noyau/seo/seo.service';
import { Bouton } from '../../partage/composants/bouton/bouton';
import { lireJetonDuLien } from '../../fonctionnalites/compte/liens-email/jeton-lien';

/** Lien d'invitation d'un membre de l'équipe : choix du mot de passe (12 caractères minimum), puis connexion avec double authentification. */
@Component({
  selector: 'bi-invitation',
  imports: [FormsModule, RouterLink, Bouton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin.scss',
  template: `
    <section class="conteneur adm-page" style="max-width: 28rem">
      <h1 class="adm-titre">Bienvenue dans l'équipe</h1>
      @if (termine()) {
        <p class="adm-info" role="status">Votre mot de passe est enregistré. À la première connexion, vous activerez la double authentification.</p>
        <p><a routerLink="/admin/connexion">Se connecter</a></p>
      } @else if (!jeton) {
        <p class="adm-erreur" role="alert">Ce lien est incomplet. Demandez une nouvelle invitation à un super-administrateur.</p>
      } @else {
        <form class="adm-formulaire" (ngSubmit)="envoyer()">
          <label class="adm-etiquette">Choisissez un mot de passe (12 caractères minimum)
            <input class="adm-saisie" name="motDePasse" type="password" autocomplete="new-password" minlength="12" required [(ngModel)]="motDePasse" />
          </label>
          @if (erreur()) { <p class="adm-erreur" role="alert">{{ erreur() }}</p> }
          <button biBouton type="submit" pleineLargeur [chargement]="envoi()">Enregistrer</button>
        </form>
      }
    </section>
  `,
})
export class Invitation implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);
  protected readonly jeton = lireJetonDuLien();
  protected motDePasse = '';
  protected readonly envoi = signal(false);
  protected readonly termine = signal(false);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    inject(SeoService).definirPage({ titre: 'Invitation', indexable: false });
  }

  ngOnInit(): void {
    this.erreur.set(null);
  }

  protected async envoyer(): Promise<void> {
    if (this.envoi() || !this.jeton) return;
    this.envoi.set(true);
    this.erreur.set(null);
    try {
      await firstValueFrom(this.http.post('/api/auth/reinitialiser', { jeton: this.jeton, motDePasse: this.motDePasse }));
      this.session.oublierLocalement();
      this.termine.set(true);
    } catch (e) {
      const lue = lireErreur(e);
      this.erreur.set(
        lue.code === 'JETON_INVALIDE' ? 'Cette invitation a expiré ou a déjà servi. Demandez-en une nouvelle.' : (lue.champs['motDePasse'] ?? lue.message),
      );
    } finally {
      this.envoi.set(false);
    }
  }
}
