import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { lireErreur } from '../../../noyau/auth/erreurs-api';
import { SessionService } from '../../../noyau/auth/session.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Champ } from '../../../partage/composants/champ/champ';
import { lireJetonDuLien } from './jeton-lien';

/** Page ouverte par le lien « mot de passe oublié » : le jeton reste en mémoire et part en POST avec le nouveau mot de passe. */
@Component({
  selector: 'bi-nouveau-mot-de-passe',
  imports: [ReactiveFormsModule, RouterLink, Bouton, Champ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../auth.scss',
  template: `
    <section class="conteneur auth">
      <h1 class="auth__titre">Nouveau mot de passe</h1>
      @if (termine()) {
        <p class="auth__succes" role="status">
          Votre mot de passe est modifié. Pour votre sécurité, vous avez été déconnecté de tous vos appareils.
        </p>
        <nav class="auth__liens" aria-label="Suite"><a routerLink="/connexion">Se connecter</a></nav>
      } @else if (!jeton) {
        <p class="auth__erreur" role="alert">Ce lien est incomplet. Demandez un nouveau lien de réinitialisation.</p>
        <nav class="auth__liens" aria-label="Suite"><a routerLink="/mot-de-passe-oublie">Mot de passe oublié</a></nav>
      } @else {
        <p class="auth__intro">Choisissez un mot de passe d’au moins 10 caractères.</p>
        <form class="auth__formulaire" [formGroup]="formulaire" (ngSubmit)="envoyer()" novalidate>
          <bi-champ
            libelle="Nouveau mot de passe"
            type="password"
            nom="motDePasse"
            autocomplete="new-password"
            requis
            formControlName="motDePasse"
            [erreur]="soumis() && formulaire.invalid ? 'Au moins 10 caractères.' : erreurServeur()"
          />
          @if (erreur()) {
            <p class="auth__erreur" role="alert">{{ erreur() }}</p>
          }
          <button biBouton type="submit" pleineLargeur [chargement]="envoi()">Enregistrer</button>
        </form>
        @if (lienExpire()) {
          <nav class="auth__liens" aria-label="Suite"><a routerLink="/mot-de-passe-oublie">Demander un nouveau lien</a></nav>
        }
      }
    </section>
  `,
})
export class NouveauMotDePasse implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly seo = inject(SeoService);
  private readonly session = inject(SessionService);
  protected readonly jeton = lireJetonDuLien();

  protected readonly envoi = signal(false);
  protected readonly termine = signal(false);
  protected readonly soumis = signal(false);
  protected readonly lienExpire = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurServeur = signal<string | null>(null);
  protected readonly formulaire = this.fb.nonNullable.group({
    motDePasse: ['', [Validators.required, Validators.minLength(10)]],
  });

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Nouveau mot de passe', indexable: false });
  }

  protected async envoyer(): Promise<void> {
    this.soumis.set(true);
    this.erreur.set(null);
    this.erreurServeur.set(null);
    if (this.formulaire.invalid || this.envoi() || !this.jeton) return;
    this.envoi.set(true);
    try {
      await firstValueFrom(
        this.http.post('/api/auth/reinitialiser', { jeton: this.jeton, motDePasse: this.formulaire.getRawValue().motDePasse }),
      );
      // Toutes les sessions sont révoquées côté serveur : celle-ci aussi
      this.session.oublierLocalement();
      this.termine.set(true);
    } catch (e) {
      const lue = lireErreur(e);
      if (lue.code === 'JETON_INVALIDE') {
        this.erreur.set('Ce lien n’est plus valide : il a expiré ou a déjà servi.');
        this.lienExpire.set(true);
      } else if (lue.champs['motDePasse']) {
        this.erreurServeur.set(lue.champs['motDePasse']);
      } else {
        this.erreur.set(lue.message);
      }
    } finally {
      this.envoi.set(false);
    }
  }
}
