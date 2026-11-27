import { ChangeDetectionStrategy, Component, ElementRef, Injector, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { lireErreur } from '../../../noyau/auth/erreurs-api';
import { focaliserPremiereErreur } from '../../../noyau/auth/focus-erreur';
import { messageMotDePasse, motDePasseValide } from '../../../noyau/auth/mot-de-passe';
import { SessionService } from '../../../noyau/auth/session.service';
import { CompteService } from '../../../noyau/compte/compte.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Champ } from '../../../partage/composants/champ/champ';
import { ReglesMotDePasse } from '../../../partage/composants/regles-mot-de-passe/regles-mot-de-passe';
import { MesDonnees } from './mes-donnees';
import { SuppressionCompte } from './suppression-compte';

const EMAIL_VALIDE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** Mot de passe et adresse e-mail : le mot de passe actuel est exigé pour les deux. */
@Component({
  selector: 'bi-securite',
  imports: [ReactiveFormsModule, Bouton, Champ, ReglesMotDePasse, MesDonnees, SuppressionCompte],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../espace.scss',
  template: `
    <h1 class="espace__titre">Sécurité</h1>

    <section aria-labelledby="titre-mdp">
      <h2 id="titre-mdp" class="espace__sous-titre">Changer de mot de passe</h2>
      <p class="espace__aide">
        Vos autres appareils seront déconnectés ; celui-ci reste connecté. Vous recevrez un e-mail de confirmation.
      </p>
      <form class="auth__formulaire" [formGroup]="mdp" (ngSubmit)="changerMotDePasse()" novalidate>
        <bi-champ libelle="Mot de passe actuel" type="password" nom="motDePasseActuel" autocomplete="current-password"
                  requis formControlName="motDePasseActuel" [erreur]="erreurMdp('motDePasseActuel')" />
        <bi-champ libelle="Nouveau mot de passe" type="password" nom="nouveauMotDePasse" autocomplete="new-password"
                  requis formControlName="nouveauMotDePasse" [erreur]="erreurMdp('nouveauMotDePasse')">
          <bi-regles-mot-de-passe [motDePasse]="mdp.controls.nouveauMotDePasse.value" />
        </bi-champ>
        @if (erreurMdpGlobale()) {
          <p class="auth__erreur" role="alert">{{ erreurMdpGlobale() }}</p>
        }
        @if (succesMdp()) {
          <p class="auth__succes" role="status">{{ succesMdp() }}</p>
        }
        <button biBouton type="submit" [chargement]="envoiMdp()">Changer le mot de passe</button>
      </form>
    </section>

    <section class="espace__section" aria-labelledby="titre-email">
      <h2 id="titre-email" class="espace__sous-titre">Changer d’adresse e-mail</h2>
      <p class="espace__aide">
        Adresse actuelle : <strong>{{ session.utilisateur()?.email }}</strong>. Un lien de confirmation est envoyé à la nouvelle
        adresse ; l’adresse actuelle reçoit une alerte avec un moyen de signaler une demande qui ne vient pas de vous.
      </p>
      <form class="auth__formulaire" [formGroup]="email" (ngSubmit)="changerEmail()" novalidate>
        <bi-champ libelle="Nouvelle adresse e-mail" type="email" nom="nouvelEmail" autocomplete="email" modeSaisie="email"
                  requis formControlName="nouvelEmail" [erreur]="erreurEmail('nouvelEmail')" />
        <bi-champ libelle="Mot de passe" type="password" nom="motDePasse" autocomplete="current-password" requis
                  formControlName="motDePasse" [erreur]="erreurEmail('motDePasse')" />
        @if (erreurEmailGlobale()) {
          <p class="auth__erreur" role="alert">{{ erreurEmailGlobale() }}</p>
        }
        @if (succesEmail()) {
          <p class="auth__succes" role="status">{{ succesEmail() }}</p>
        }
        <button biBouton type="submit" [chargement]="envoiEmail()">Envoyer le lien de confirmation</button>
      </form>
    </section>

    <bi-mes-donnees />
    <bi-suppression-compte />
  `,
})
export class Securite implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly compte = inject(CompteService);
  private readonly seo = inject(SeoService);
  private readonly racine = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);
  protected readonly session = inject(SessionService);

  protected readonly mdp = this.fb.nonNullable.group({
    motDePasseActuel: ['', Validators.required],
    nouveauMotDePasse: ['', [Validators.required, motDePasseValide]],
  });
  protected readonly email = this.fb.nonNullable.group({
    nouvelEmail: ['', [Validators.required, Validators.pattern(EMAIL_VALIDE)]],
    motDePasse: ['', Validators.required],
  });

  protected readonly soumisMdp = signal(false);
  protected readonly envoiMdp = signal(false);
  protected readonly succesMdp = signal<string | null>(null);
  protected readonly erreurMdpGlobale = signal<string | null>(null);
  protected readonly serveurMdp = signal<Record<string, string>>({});

  protected readonly soumisEmail = signal(false);
  protected readonly envoiEmail = signal(false);
  protected readonly succesEmail = signal<string | null>(null);
  protected readonly erreurEmailGlobale = signal<string | null>(null);
  protected readonly serveurEmail = signal<Record<string, string>>({});

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Sécurité du compte', indexable: false });
  }

  protected erreurMdp(nom: 'motDePasseActuel' | 'nouveauMotDePasse'): string | null {
    const serveur = this.serveurMdp()[nom];
    if (serveur) return serveur;
    if (!this.soumisMdp() || this.mdp.controls[nom].valid) return null;
    return nom === 'motDePasseActuel'
      ? 'Saisissez votre mot de passe actuel.'
      : messageMotDePasse(this.mdp.controls.nouveauMotDePasse.value);
  }

  protected erreurEmail(nom: 'nouvelEmail' | 'motDePasse'): string | null {
    const serveur = this.serveurEmail()[nom];
    if (serveur) return serveur;
    if (!this.soumisEmail() || this.email.controls[nom].valid) return null;
    return nom === 'nouvelEmail'
      ? 'Saisissez la nouvelle adresse e-mail, par exemple nom@exemple.sn.'
      : 'Saisissez votre mot de passe pour confirmer.';
  }

  protected async changerMotDePasse(): Promise<void> {
    this.soumisMdp.set(true);
    this.succesMdp.set(null);
    this.erreurMdpGlobale.set(null);
    this.serveurMdp.set({});
    if (this.mdp.invalid) {
      focaliserPremiereErreur(this.racine, this.injector);
      return;
    }
    if (this.envoiMdp()) return;
    this.envoiMdp.set(true);
    try {
      const v = this.mdp.getRawValue();
      this.succesMdp.set(await this.compte.changerMotDePasse(v.motDePasseActuel, v.nouveauMotDePasse));
      this.mdp.reset({ motDePasseActuel: '', nouveauMotDePasse: '' });
      this.soumisMdp.set(false);
    } catch (e) {
      const lue = lireErreur(e);
      if (lue.code === 'MOT_DE_PASSE_ACTUEL_INCORRECT') {
        this.serveurMdp.set({ motDePasseActuel: 'Le mot de passe actuel est incorrect.' });
        focaliserPremiereErreur(this.racine, this.injector);
      } else if (lue.code === 'MOT_DE_PASSE_IDENTIQUE') {
        this.serveurMdp.set({ nouveauMotDePasse: lue.message });
        focaliserPremiereErreur(this.racine, this.injector);
      } else if (Object.keys(lue.champs).length > 0) {
        this.serveurMdp.set(lue.champs);
        focaliserPremiereErreur(this.racine, this.injector);
      } else {
        this.erreurMdpGlobale.set(lue.message);
      }
    } finally {
      this.envoiMdp.set(false);
    }
  }

  protected async changerEmail(): Promise<void> {
    this.soumisEmail.set(true);
    this.succesEmail.set(null);
    this.erreurEmailGlobale.set(null);
    this.serveurEmail.set({});
    if (this.email.invalid) {
      focaliserPremiereErreur(this.racine, this.injector);
      return;
    }
    if (this.envoiEmail()) return;
    this.envoiEmail.set(true);
    try {
      const v = this.email.getRawValue();
      this.succesEmail.set(await this.compte.changerEmail(v.motDePasse, v.nouvelEmail));
      this.email.reset({ nouvelEmail: '', motDePasse: '' });
      this.soumisEmail.set(false);
    } catch (e) {
      const lue = lireErreur(e);
      if (lue.code === 'MOT_DE_PASSE_ACTUEL_INCORRECT') {
        this.serveurEmail.set({ motDePasse: 'Le mot de passe est incorrect.' });
        focaliserPremiereErreur(this.racine, this.injector);
      } else if (lue.code === 'EMAIL_IDENTIQUE') {
        this.serveurEmail.set({ nouvelEmail: lue.message });
        focaliserPremiereErreur(this.racine, this.injector);
      } else if (Object.keys(lue.champs).length > 0) {
        this.serveurEmail.set(lue.champs);
        focaliserPremiereErreur(this.racine, this.injector);
      } else {
        this.erreurEmailGlobale.set(lue.message);
      }
    } finally {
      this.envoiEmail.set(false);
    }
  }
}
