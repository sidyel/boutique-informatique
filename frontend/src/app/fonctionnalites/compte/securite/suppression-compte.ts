import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { lireErreur } from '../../../noyau/auth/erreurs-api';
import { SessionService } from '../../../noyau/auth/session.service';
import { CompteService } from '../../../noyau/compte/compte.service';
import { PanierService } from '../../../noyau/panier/panier.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Champ } from '../../../partage/composants/champ/champ';

const MOT_CONFIRMATION = 'SUPPRIMER';

/** Suppression du compte : confirmation renforcée (mot de passe + mot à saisir) ; refusée si une commande est en cours. */
@Component({
  selector: 'bi-suppression-compte',
  imports: [ReactiveFormsModule, RouterLink, Bouton, Champ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../espace.scss',
  template: `
    <section class="espace__section espace__danger" aria-labelledby="titre-suppression">
      <h2 id="titre-suppression" class="espace__sous-titre">Supprimer mon compte</h2>
      <p class="espace__aide">C’est définitif. Voici ce qui se passe :</p>
      <ul class="espace__liste">
        <li>Vos adresses, votre panier, vos connexions et vos alertes sont effacés.</li>
        <li>
          Vos factures sont conservées pendant la durée légale, avec les coordonnées qu’elles exigent ; les données de
          suivi publicitaire de vos commandes sont effacées.
        </li>
        <li>Vos avis restent publiés, signés « Client vérifié » ou « Client ».</li>
        <li>La suppression est refusée tant qu’une commande n’est pas livrée, annulée ou remboursée.</li>
      </ul>
      <form class="auth__formulaire" [formGroup]="formulaire" (ngSubmit)="supprimer()" novalidate>
        <bi-champ libelle="Mot de passe" type="password" nom="motDePasseSuppression" autocomplete="current-password" requis
                  formControlName="motDePasse" [erreur]="erreurChamp('motDePasse')" />
        <bi-champ libelle="Pour confirmer, saisissez SUPPRIMER" nom="confirmationSuppression" autocomplete="off"
                  requis formControlName="confirmation" [erreur]="erreurChamp('confirmation')" />
        @if (erreur()) {
          <p class="auth__erreur" role="alert">
            {{ erreur() }}
            @if (commandeEnCours()) {
              <a routerLink="/compte/commandes">Voir mes commandes</a>
            }
          </p>
        }
        <button biBouton variante="danger" type="submit" [chargement]="envoi()">Supprimer définitivement mon compte</button>
      </form>
    </section>
  `,
})
export class SuppressionCompte {
  private readonly compte = inject(CompteService);
  private readonly session = inject(SessionService);
  private readonly panier = inject(PanierService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly formulaire = this.fb.nonNullable.group({
    motDePasse: ['', Validators.required],
    confirmation: ['', Validators.required],
  });
  protected readonly soumis = signal(false);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly commandeEnCours = signal(false);
  protected readonly erreurServeur = signal<Record<string, string>>({});

  protected erreurChamp(nom: 'motDePasse' | 'confirmation'): string | null {
    const serveur = this.erreurServeur()[nom];
    if (serveur) return serveur;
    if (!this.soumis()) return null;
    if (nom === 'motDePasse') return this.formulaire.controls.motDePasse.valid ? null : 'Saisissez votre mot de passe.';
    return this.confirmationJuste() ? null : `Saisissez le mot ${MOT_CONFIRMATION} pour confirmer.`;
  }

  private confirmationJuste(): boolean {
    return this.formulaire.controls.confirmation.value.trim().toUpperCase() === MOT_CONFIRMATION;
  }

  protected async supprimer(): Promise<void> {
    this.soumis.set(true);
    this.erreur.set(null);
    this.commandeEnCours.set(false);
    this.erreurServeur.set({});
    if (this.formulaire.invalid || !this.confirmationJuste() || this.envoi()) return;
    this.envoi.set(true);
    try {
      const v = this.formulaire.getRawValue();
      await this.compte.supprimerCompte(v.motDePasse, v.confirmation.trim());
      // Le serveur a révoqué toutes les sessions : on oublie la session et le panier ici aussi
      this.session.oublierLocalement();
      this.panier.reinitialiserLocalement();
      await this.router.navigateByUrl('/compte-supprime');
    } catch (e) {
      const lue = lireErreur(e);
      if (lue.code === 'MOT_DE_PASSE_ACTUEL_INCORRECT') {
        this.erreurServeur.set({ motDePasse: 'Le mot de passe est incorrect.' });
      } else if (lue.code === 'CONFIRMATION_INCORRECTE') {
        this.erreurServeur.set({ confirmation: lue.message });
      } else {
        this.commandeEnCours.set(lue.code === 'COMMANDE_EN_COURS');
        this.erreur.set(lue.message);
      }
    } finally {
      this.envoi.set(false);
    }
  }
}
