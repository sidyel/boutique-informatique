import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { lireErreur } from '../../noyau/auth/erreurs-api';
import { SessionService } from '../../noyau/auth/session.service';
import { Bouton } from '../../partage/composants/bouton/bouton';
import { ApiAdminService, Moi } from '../noyau/api-admin.service';

/**
 * Cadre de l'administration : navigation selon le rôle, avertissements permanents (un seul super-administrateur,
 * codes de secours presque épuisés) et fenêtre de réauthentification (code TOTP) pour les actions sensibles.
 */
@Component({
  selector: 'bi-cadre-admin',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, Bouton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin.scss',
  styles: `
    .cadre__barre { background: var(--couleur-sombre); color: var(--couleur-surface); }
    .cadre__nav { display: flex; flex-wrap: wrap; gap: var(--espace-1); align-items: center; padding-block: var(--espace-2); }
    .cadre__lien { display: inline-flex; align-items: center; min-height: 2.75rem; padding-inline: var(--espace-3); color: inherit; text-decoration: none; border-radius: var(--rayon-sm); }
    .cadre__lien.actif { background: var(--couleur-accent); }
    .cadre__espace { flex: 1; }
    .cadre__alertes { display: flex; flex-direction: column; gap: var(--espace-2); padding-block: var(--espace-3) 0; }
    .cadre__fenetre { position: fixed; inset: 0; z-index: var(--z-modale, 100); display: grid; place-items: center; padding: var(--espace-4); background: rgb(0 0 0 / 0.5); }
    .cadre__boite { width: min(24rem, 100%); }
  `,
  template: `
    <header class="cadre__barre">
      <nav class="conteneur cadre__nav" aria-label="Administration">
        <a class="cadre__lien" routerLink="/admin" routerLinkActive="actif" [routerLinkActiveOptions]="{ exact: true }">Tableau de bord</a>
        @if (estSuperAdmin()) {
          <a class="cadre__lien" routerLink="/admin/utilisateurs" routerLinkActive="actif">Équipe</a>
          <a class="cadre__lien" routerLink="/admin/journal" routerLinkActive="actif">Journal</a>
        }
        <span class="cadre__espace"></span>
        <a class="cadre__lien" routerLink="/admin/mon-compte" routerLinkActive="actif">Mon compte</a>
        <button biBouton type="button" variante="fantome" (click)="deconnecter()">Se déconnecter</button>
      </nav>
    </header>

    @if (avertissements().length) {
      <div class="conteneur cadre__alertes">
        @for (a of avertissements(); track a.code) {
          <p class="adm-alerte" role="status">{{ a.message }}</p>
        }
      </div>
    }

    <router-outlet />

    @if (api.reauthentificationOuverte()) {
      <div class="cadre__fenetre" role="dialog" aria-modal="true" aria-labelledby="reauth-titre">
        <form class="adm-carte adm-formulaire cadre__boite" (ngSubmit)="valider()">
          <h2 id="reauth-titre" class="adm-titre">Confirmez avec votre code</h2>
          <p>Cette action est sensible. Saisissez le code à 6 chiffres de votre application d'authentification.</p>
          <input class="adm-saisie adm-code" name="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" required
                 aria-label="Code à 6 chiffres" [(ngModel)]="code" />
          @if (erreur()) { <p class="adm-erreur" role="alert">{{ erreur() }}</p> }
          <div class="adm-actions">
            <button biBouton type="submit" [chargement]="envoi()">Confirmer</button>
            <button biBouton type="button" variante="fantome" (click)="annuler()">Annuler</button>
          </div>
        </form>
      </div>
    }
  `,
})
export class CadreAdmin implements OnInit {
  protected readonly api = inject(ApiAdminService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly moi = signal<Moi | null>(null);
  protected readonly avertissements = computed(() => this.moi()?.avertissements ?? []);
  protected readonly estSuperAdmin = computed(() => this.session.utilisateur()?.role === 'SUPER_ADMIN');
  protected readonly erreur = signal<string | null>(null);
  protected readonly envoi = signal(false);
  protected code = '';

  async ngOnInit(): Promise<void> {
    try {
      this.moi.set(await this.api.get<Moi>('/api/admin/moi'));
    } catch {
      // Le cadre reste utilisable ; l'intercepteur gère l'expiration de session
    }
  }

  protected async valider(): Promise<void> {
    if (this.envoi()) return;
    this.envoi.set(true);
    this.erreur.set(null);
    try {
      await this.api.validerCode(this.code.trim());
      this.code = '';
    } catch (e) {
      this.erreur.set(lireErreur(e).message);
    } finally {
      this.envoi.set(false);
    }
  }

  protected annuler(): void {
    this.code = '';
    this.erreur.set(null);
    this.api.annulerCode();
  }

  protected async deconnecter(): Promise<void> {
    await this.session.deconnecter();
    await this.router.navigateByUrl('/admin/connexion');
  }
}
