import { ChangeDetectionStrategy, Component, ElementRef, Injector, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { lireErreur } from '../../../noyau/auth/erreurs-api';
import { focaliserPremiereErreur } from '../../../noyau/auth/focus-erreur';
import { SessionService } from '../../../noyau/auth/session.service';
import { CompteService } from '../../../noyau/compte/compte.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Champ } from '../../../partage/composants/champ/champ';

const SOURCES = { INSCRIPTION: 'à l’inscription', COMPTE: 'depuis votre compte', ADMIN: 'à votre demande, par notre équipe' };

/** Nom, téléphone (stocké en E.164 par le serveur) et consentement marketing. */
@Component({
  selector: 'bi-profil',
  imports: [ReactiveFormsModule, Bouton, Champ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../espace.scss',
  template: `
    <h1 class="espace__titre">Profil</h1>
    <p class="auth__intro">Vos coordonnées servent à vous livrer et à vous joindre au sujet d’une commande.</p>
    <form class="auth__formulaire" [formGroup]="formulaire" (ngSubmit)="enregistrer()" novalidate>
      <bi-champ libelle="Prénom" nom="prenom" autocomplete="given-name" requis formControlName="prenom"
                [erreur]="erreurChamp('prenom', 'Saisissez votre prénom.')" />
      <bi-champ libelle="Nom" nom="nom" autocomplete="family-name" requis formControlName="nom"
                [erreur]="erreurChamp('nom', 'Saisissez votre nom.')" />
      <bi-champ libelle="Téléphone" type="tel" nom="telephone" autocomplete="tel" modeSaisie="tel"
                aide="Numéro sénégalais, par exemple 77 123 45 67. Laissez vide pour l’effacer."
                formControlName="telephone" [erreur]="erreurChamp('telephone', '')" />
      @if (erreur()) {
        <p class="auth__erreur" role="alert">{{ erreur() }}</p>
      }
      @if (succes()) {
        <p class="auth__succes" role="status">Profil enregistré.</p>
      }
      <button biBouton type="submit" [chargement]="envoi()">Enregistrer</button>
    </form>

    <section class="espace__section" aria-labelledby="titre-consentement">
      <h2 id="titre-consentement">Offres par e-mail</h2>
      @if (session.utilisateur(); as client) {
        <p class="espace__aide">
          @if (client.consentementMarketing) {
            Vous recevez nos offres et nouveautés par e-mail.
          } @else {
            Vous ne recevez pas nos offres par e-mail. Les e-mails liés à vos commandes continuent d’être envoyés.
          }
          @if (client.dateConsentement) {
            <br />Dernier choix le {{ dateLisible(client.dateConsentement) }},
            {{ source(client.sourceConsentement) }}.
          }
        </p>
        @if (erreurConsentement()) {
          <p class="auth__erreur" role="alert">{{ erreurConsentement() }}</p>
        }
        <button biBouton [variante]="client.consentementMarketing ? 'contour' : 'principal'" type="button"
                [chargement]="envoiConsentement()" (click)="basculer(!client.consentementMarketing)">
          {{ client.consentementMarketing ? 'Ne plus recevoir les offres' : 'Recevoir les offres' }}
        </button>
      }
    </section>
  `,
})
export class Profil implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly compte = inject(CompteService);
  private readonly seo = inject(SeoService);
  private readonly racine = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);
  protected readonly session = inject(SessionService);

  protected readonly envoi = signal(false);
  protected readonly envoiConsentement = signal(false);
  protected readonly soumis = signal(false);
  protected readonly succes = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurConsentement = signal<string | null>(null);
  protected readonly erreursServeur = signal<Record<string, string>>({});

  protected readonly formulaire = this.fb.nonNullable.group({
    prenom: [this.session.utilisateur()?.prenom ?? '', [Validators.required, Validators.maxLength(80)]],
    nom: [this.session.utilisateur()?.nom ?? '', [Validators.required, Validators.maxLength(80)]],
    telephone: [this.session.utilisateur()?.telephone ?? ''],
  });

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Mon profil', indexable: false });
  }

  protected dateLisible(iso: string): string {
    return new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
  }

  protected source(source: string | null): string {
    return source ? (SOURCES[source as keyof typeof SOURCES] ?? '') : '';
  }

  protected erreurChamp(nom: 'prenom' | 'nom' | 'telephone', obligatoire: string): string | null {
    const serveur = this.erreursServeur()[nom];
    if (serveur) return serveur;
    return this.soumis() && obligatoire && this.formulaire.controls[nom].invalid ? obligatoire : null;
  }

  protected async enregistrer(): Promise<void> {
    this.soumis.set(true);
    this.erreur.set(null);
    this.succes.set(false);
    this.erreursServeur.set({});
    if (this.formulaire.invalid) {
      focaliserPremiereErreur(this.racine, this.injector);
      return;
    }
    if (this.envoi()) return;
    this.envoi.set(true);
    try {
      const v = this.formulaire.getRawValue();
      const profil = await this.compte.modifierProfil(v.prenom, v.nom, v.telephone);
      this.formulaire.patchValue({ prenom: profil.prenom, nom: profil.nom, telephone: profil.telephone ?? '' });
      this.succes.set(true);
    } catch (e) {
      const lue = lireErreur(e);
      if (Object.keys(lue.champs).length > 0) {
        this.erreursServeur.set(lue.champs);
        focaliserPremiereErreur(this.racine, this.injector);
      } else {
        this.erreur.set(lue.message);
      }
    } finally {
      this.envoi.set(false);
    }
  }

  protected async basculer(accepte: boolean): Promise<void> {
    this.erreurConsentement.set(null);
    this.envoiConsentement.set(true);
    try {
      await this.compte.definirConsentement(accepte);
    } catch (e) {
      this.erreurConsentement.set(lireErreur(e).message);
    } finally {
      this.envoiConsentement.set(false);
    }
  }
}
