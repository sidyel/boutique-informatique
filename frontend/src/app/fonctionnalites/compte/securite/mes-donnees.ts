import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { lireErreur } from '../../../noyau/auth/erreurs-api';
import { CompteService } from '../../../noyau/compte/compte.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Champ } from '../../../partage/composants/champ/champ';

/** Droit d'accès : un fichier JSON lisible avec tout ce que la boutique garde sur le client. Mot de passe exigé. */
@Component({
  selector: 'bi-mes-donnees',
  imports: [ReactiveFormsModule, Bouton, Champ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../espace.scss',
  template: `
    <section class="espace__section" aria-labelledby="titre-donnees">
      <h2 id="titre-donnees" class="espace__sous-titre">Télécharger mes données</h2>
      <p class="espace__aide">
        Un fichier unique avec votre profil, vos adresses, vos commandes, vos consentements, vos changements d’adresse
        e-mail et vos avis. Le mot de passe est demandé : le fichier contient vos coordonnées.
      </p>
      <form class="auth__formulaire" [formGroup]="formulaire" (ngSubmit)="telecharger()" novalidate>
        <bi-champ libelle="Mot de passe" type="password" nom="motDePasseDonnees" autocomplete="current-password" requis
                  formControlName="motDePasse" [erreur]="erreurChamp()" />
        @if (erreur()) {
          <p class="auth__erreur" role="alert">{{ erreur() }}</p>
        }
        @if (reussi()) {
          <p class="auth__succes" role="status">Le fichier « mes-donnees-boutique.json » a été téléchargé.</p>
        }
        <button biBouton variante="contour" type="submit" [chargement]="envoi()">Télécharger mes données</button>
      </form>
    </section>
  `,
})
export class MesDonnees {
  private readonly compte = inject(CompteService);
  private readonly document = inject(DOCUMENT);
  private readonly fb = inject(FormBuilder);

  protected readonly formulaire = this.fb.nonNullable.group({ motDePasse: ['', Validators.required] });
  protected readonly soumis = signal(false);
  protected readonly envoi = signal(false);
  protected readonly reussi = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurServeur = signal<string | null>(null);

  protected erreurChamp(): string | null {
    if (this.erreurServeur()) return this.erreurServeur();
    return this.soumis() && this.formulaire.invalid ? 'Saisissez votre mot de passe.' : null;
  }

  protected async telecharger(): Promise<void> {
    this.soumis.set(true);
    this.reussi.set(false);
    this.erreur.set(null);
    this.erreurServeur.set(null);
    if (this.formulaire.invalid || this.envoi()) return;
    this.envoi.set(true);
    try {
      const donnees = await this.compte.mesDonnees(this.formulaire.getRawValue().motDePasse);
      // Fichier fabriqué dans le navigateur : rien n'est mis en cache ni envoyé ailleurs
      const blob = new Blob([JSON.stringify(donnees, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const lien = this.document.createElement('a');
      lien.href = url;
      lien.download = 'mes-donnees-boutique.json';
      lien.click();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
      this.formulaire.reset({ motDePasse: '' });
      this.soumis.set(false);
      this.reussi.set(true);
    } catch (e) {
      const lue = lireErreur(e);
      if (lue.code === 'MOT_DE_PASSE_ACTUEL_INCORRECT') this.erreurServeur.set('Le mot de passe est incorrect.');
      else this.erreur.set(lue.message);
    } finally {
      this.envoi.set(false);
    }
  }
}
