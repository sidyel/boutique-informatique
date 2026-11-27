import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, ElementRef, Injector, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { lireErreur } from '../../../noyau/auth/erreurs-api';
import { focaliserPremiereErreur } from '../../../noyau/auth/focus-erreur';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Champ } from '../../../partage/composants/champ/champ';

@Component({
  selector: 'bi-mot-de-passe-oublie',
  imports: [ReactiveFormsModule, RouterLink, Bouton, Champ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../auth.scss',
  template: `
    <section class="conteneur auth">
      <h1 class="auth__titre">Mot de passe oublié</h1>
      @if (termine()) {
        <p class="auth__succes" role="status">
          Si un compte existe pour cette adresse, un e-mail avec un lien de réinitialisation vient d’être envoyé. Le
          lien est valable une heure.
        </p>
      } @else {
        <p class="auth__intro">
          Indiquez votre adresse e-mail : nous vous envoyons un lien pour choisir un nouveau mot de passe.
        </p>
        <form class="auth__formulaire" [formGroup]="formulaire" (ngSubmit)="envoyer()" novalidate>
          <bi-champ
            libelle="Adresse e-mail"
            type="email"
            nom="email"
            autocomplete="email"
            modeSaisie="email"
            requis
            formControlName="email"
            [erreur]="soumis() && formulaire.invalid ? 'Saisissez une adresse e-mail valide, par exemple nom@exemple.sn.' : null"
          />
          @if (erreur()) {
            <p class="auth__erreur" role="alert">{{ erreur() }}</p>
          }
          <button biBouton type="submit" pleineLargeur [chargement]="envoi()">Envoyer le lien</button>
        </form>
      }
      <nav class="auth__liens" aria-label="Autres actions">
        <a routerLink="/connexion">Retour à la connexion</a>
      </nav>
    </section>
  `,
})
export class MotDePasseOublie implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly seo = inject(SeoService);
  private readonly racine = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);

  protected readonly envoi = signal(false);
  protected readonly termine = signal(false);
  protected readonly soumis = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly formulaire = this.fb.nonNullable.group({ email: ['', [Validators.required, Validators.email]] });

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Mot de passe oublié', indexable: false });
  }

  protected async envoyer(): Promise<void> {
    this.soumis.set(true);
    this.erreur.set(null);
    if (this.formulaire.invalid) {
      focaliserPremiereErreur(this.racine, this.injector);
      return;
    }
    if (this.envoi()) return;
    this.envoi.set(true);
    try {
      await firstValueFrom(
        this.http.post('/api/auth/mot-de-passe-oublie', { email: this.formulaire.getRawValue().email.trim() }),
      );
      this.termine.set(true);
    } catch (e) {
      this.erreur.set(lireErreur(e).message);
    } finally {
      this.envoi.set(false);
    }
  }
}
