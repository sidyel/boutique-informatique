import { ChangeDetectionStrategy, Component, ElementRef, Injector, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { lireErreur, retourSur } from '../../../noyau/auth/erreurs-api';
import { focaliserPremiereErreur } from '../../../noyau/auth/focus-erreur';
import { EtapeEquipeRequise, SessionService } from '../../../noyau/auth/session.service';
import { PanierService } from '../../../noyau/panier/panier.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Champ } from '../../../partage/composants/champ/champ';

@Component({
  selector: 'bi-connexion',
  imports: [ReactiveFormsModule, RouterLink, Bouton, Champ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../auth.scss',
  template: `
    <section class="conteneur auth">
      <h1 class="auth__titre">Connexion</h1>
      <p class="auth__intro">Retrouvez vos commandes, vos factures et vos adresses.</p>

      <form class="auth__formulaire" [formGroup]="formulaire" (ngSubmit)="envoyer()" novalidate>
        <bi-champ
          libelle="Adresse e-mail"
          type="email"
          nom="email"
          autocomplete="username"
          modeSaisie="email"
          requis
          formControlName="email"
          [erreur]="erreurChamp('email')"
        />
        <bi-champ
          libelle="Mot de passe"
          type="password"
          nom="motDePasse"
          autocomplete="current-password"
          requis
          formControlName="motDePasse"
          [erreur]="erreurChamp('motDePasse')"
        />
        @if (erreur()) {
          <p class="auth__erreur" role="alert">{{ erreur() }}</p>
        }
        <button biBouton type="submit" pleineLargeur [chargement]="envoi()">Se connecter</button>
      </form>

      <nav class="auth__liens" aria-label="Autres actions">
        <a routerLink="/mot-de-passe-oublie">Mot de passe oublié ?</a>
        <a routerLink="/inscription" [queryParams]="retour ? { retour } : null">Créer un compte</a>
      </nav>
    </section>
  `,
})
export class Connexion implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly session = inject(SessionService);
  private readonly panier = inject(PanierService);
  private readonly router = inject(Router);
  private readonly seo = inject(SeoService);
  private readonly racine = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);
  protected readonly retour = inject(ActivatedRoute).snapshot.queryParamMap.get('retour');

  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly soumis = signal(false);

  protected readonly formulaire = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    motDePasse: ['', Validators.required],
  });

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Connexion', indexable: false });
  }

  protected erreurChamp(nom: 'email' | 'motDePasse'): string | null {
    const c = this.formulaire.controls[nom];
    if (!this.soumis() || c.valid) return null;
    return nom === 'email'
      ? 'Saisissez votre adresse e-mail, par exemple nom@exemple.sn.'
      : 'Saisissez votre mot de passe.';
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
      const { email, motDePasse } = this.formulaire.getRawValue();
      await this.session.connecter(email.trim(), motDePasse);
      // Le panier invité est fusionné par le serveur à la première lecture du client connecté
      await this.panier.charger();
      await this.router.navigateByUrl(retourSur(this.retour));
    } catch (e) {
      if (e instanceof EtapeEquipeRequise) {
        // Compte de l'équipe : la suite (code TOTP) se fait sur la page d'administration
        await this.router.navigate(['/admin/connexion'], { state: { etape: e.etape, email: this.formulaire.getRawValue().email.trim() } });
        return;
      }
      this.erreur.set(lireErreur(e).message);
      // Identifiants refusés : le focus revient sur le mot de passe, prêt à être ressaisi
      const champ = this.racine.querySelector<HTMLInputElement>('input[name=motDePasse]');
      champ?.focus();
      champ?.select();
    } finally {
      this.envoi.set(false);
    }
  }
}
