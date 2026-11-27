import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, ElementRef, Injector, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { lireErreur } from '../../../noyau/auth/erreurs-api';
import { focaliserPremiereErreur } from '../../../noyau/auth/focus-erreur';
import { messageMotDePasse, motDePasseValide } from '../../../noyau/auth/mot-de-passe';
import { SeoService } from '../../../noyau/seo/seo.service';
import { normaliserTelephone } from '../../../noyau/telephone-senegal';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Champ } from '../../../partage/composants/champ/champ';
import { ReglesMotDePasse } from '../../../partage/composants/regles-mot-de-passe/regles-mot-de-passe';

@Component({
  selector: 'bi-inscription',
  imports: [ReactiveFormsModule, RouterLink, Bouton, Champ, ReglesMotDePasse],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../auth.scss',
  template: `
    <section class="conteneur auth">
      <h1 class="auth__titre">Créer un compte</h1>

      @if (termine()) {
        <p class="auth__succes" role="status">
          Un e-mail de confirmation vient d’être envoyé. Cliquez sur le lien qu’il contient pour activer votre
          adresse. Pensez à vérifier vos courriers indésirables.
        </p>
        <nav class="auth__liens" aria-label="Suite">
          <a routerLink="/connexion">Se connecter</a>
        </nav>
      } @else {
        <p class="auth__intro">
          Suivez vos commandes et retrouvez vos factures. Vous pouvez aussi commander sans compte.
        </p>
        <form class="auth__formulaire" [formGroup]="formulaire" (ngSubmit)="envoyer()" novalidate>
          <bi-champ libelle="Prénom" nom="prenom" autocomplete="given-name" requis formControlName="prenom"
                    [erreur]="erreurChamp('prenom')" />
          <bi-champ libelle="Nom" nom="nom" autocomplete="family-name" requis formControlName="nom"
                    [erreur]="erreurChamp('nom')" />
          <bi-champ libelle="Adresse e-mail" type="email" nom="email" autocomplete="email" modeSaisie="email"
                    requis formControlName="email" [erreur]="erreurChamp('email')" />
          <bi-champ libelle="Téléphone (facultatif)" type="tel" nom="telephone" prefixe="+221"
                    autocomplete="tel-national" modeSaisie="tel" aide="Utile pour la livraison."
                    formControlName="telephone" [erreur]="erreurChamp('telephone')" />
          <bi-champ libelle="Mot de passe" type="password" nom="motDePasse" autocomplete="new-password"
                    requis formControlName="motDePasse" [erreur]="erreurChamp('motDePasse')">
            <bi-regles-mot-de-passe [motDePasse]="formulaire.controls.motDePasse.value" />
          </bi-champ>
          <label class="auth__case">
            <input type="checkbox" formControlName="consentementMarketing" />
            <span>J’accepte de recevoir des offres par e-mail (facultatif, désinscription à tout moment).</span>
          </label>
          @if (erreur()) {
            <p class="auth__erreur" role="alert">{{ erreur() }}</p>
          }
          <button biBouton type="submit" pleineLargeur [chargement]="envoi()">Créer mon compte</button>
        </form>
        <nav class="auth__liens" aria-label="Autres actions">
          <a routerLink="/connexion">J’ai déjà un compte</a>
        </nav>
      }
    </section>
  `,
})
export class Inscription implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly seo = inject(SeoService);
  private readonly racine = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);

  protected readonly envoi = signal(false);
  protected readonly termine = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly soumis = signal(false);
  private readonly erreursServeur = signal<Record<string, string>>({});

  protected readonly formulaire = this.fb.nonNullable.group({
    prenom: ['', [Validators.required, Validators.maxLength(80)]],
    nom: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email]],
    telephone: [''],
    motDePasse: ['', [Validators.required, motDePasseValide]],
    consentementMarketing: [false],
  });

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Créer un compte', indexable: false });
  }

  protected erreurChamp(nom: string): string | null {
    if (this.erreursServeur()[nom]) return this.erreursServeur()[nom];
    if (!this.soumis()) return null;
    const v = this.formulaire.getRawValue();
    switch (nom) {
      case 'prenom':
        return v.prenom.trim() ? null : 'Saisissez votre prénom.';
      case 'nom':
        return v.nom.trim() ? null : 'Saisissez votre nom.';
      case 'email':
        return this.formulaire.controls.email.valid
          ? null
          : 'Saisissez une adresse e-mail valide, par exemple nom@exemple.sn.';
      case 'telephone':
        return !v.telephone.trim() || normaliserTelephone(v.telephone) ? null : 'Numéro invalide (ex. 77 123 45 67).';
      case 'motDePasse':
        return messageMotDePasse(v.motDePasse);
      default:
        return null;
    }
  }

  protected async envoyer(): Promise<void> {
    this.soumis.set(true);
    this.erreur.set(null);
    this.erreursServeur.set({});
    const v = this.formulaire.getRawValue();
    if (['prenom', 'nom', 'email', 'telephone', 'motDePasse'].some((c) => this.erreurChamp(c))) {
      focaliserPremiereErreur(this.racine, this.injector);
      return;
    }
    if (this.envoi()) return;
    this.envoi.set(true);
    try {
      await firstValueFrom(
        this.http.post('/api/auth/inscription', {
          email: v.email.trim(),
          motDePasse: v.motDePasse,
          prenom: v.prenom.trim(),
          nom: v.nom.trim(),
          telephone: v.telephone.trim() ? normaliserTelephone(v.telephone) : null,
          consentementMarketing: v.consentementMarketing,
        }),
      );
      this.termine.set(true);
    } catch (e) {
      const lue = lireErreur(e);
      this.erreursServeur.set(lue.champs);
      this.erreur.set(Object.keys(lue.champs).length ? null : lue.message);
      focaliserPremiereErreur(this.racine, this.injector);
    } finally {
      this.envoi.set(false);
    }
  }
}
