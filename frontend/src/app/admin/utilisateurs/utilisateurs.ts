import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { lireErreur } from '../../noyau/auth/erreurs-api';
import { SeoService } from '../../noyau/seo/seo.service';
import { Bouton } from '../../partage/composants/bouton/bouton';
import { ApiAdminService, LIBELLES_ROLES, RoleEquipe } from '../noyau/api-admin.service';

interface CompteEquipe {
  id: number;
  email: string;
  prenom: string;
  nom: string;
  role: RoleEquipe;
  actif: boolean;
  secondFacteurActif: boolean;
  invitationEnAttente: boolean;
  codesSecoursRestants: number;
}

/** Gestion de l'équipe (SUPER_ADMIN) : invitation, rôle, désactivation, réinitialisation du second facteur. Chaque action demande un code TOTP. */
@Component({
  selector: 'bi-utilisateurs',
  imports: [FormsModule, Bouton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin.scss',
  template: `
    <section class="conteneur adm-page">
      <h1 class="adm-titre">Équipe</h1>
      @if (message()) { <p class="adm-info" role="status">{{ message() }}</p> }
      @if (erreur()) { <p class="adm-erreur" role="alert">{{ erreur() }}</p> }

      <form class="adm-carte adm-formulaire" (ngSubmit)="inviter()" style="margin-bottom: var(--espace-6)">
        <h2 style="margin: 0; font-size: var(--taille-lg)">Inviter un membre</h2>
        <label class="adm-etiquette">Adresse e-mail
          <input class="adm-saisie" name="email" type="email" inputmode="email" autocomplete="off" required [(ngModel)]="invitation.email" />
        </label>
        <label class="adm-etiquette">Prénom
          <input class="adm-saisie" name="prenom" autocomplete="off" required [(ngModel)]="invitation.prenom" />
        </label>
        <label class="adm-etiquette">Nom
          <input class="adm-saisie" name="nom" autocomplete="off" required [(ngModel)]="invitation.nom" />
        </label>
        <label class="adm-etiquette">Rôle
          <select class="adm-saisie" name="role" [(ngModel)]="invitation.role">
            @for (r of roles; track r) { <option [value]="r">{{ libelle(r) }}</option> }
          </select>
        </label>
        <button biBouton type="submit" [chargement]="envoi()">Envoyer l'invitation</button>
      </form>

      <div class="adm-tableau-conteneur">
        <table class="adm-tableau">
          <thead><tr><th>Compte</th><th>Rôle</th><th>État</th><th>Actions</th></tr></thead>
          <tbody>
            @for (c of comptes(); track c.id) {
              <tr>
                <td>{{ c.prenom }} {{ c.nom }}<br /><span class="adm-mono">{{ c.email }}</span></td>
                <td>
                  <select class="adm-saisie" [attr.aria-label]="'Rôle de ' + c.email" [ngModel]="c.role" (ngModelChange)="changerRole(c, $event)">
                    @for (r of roles; track r) { <option [value]="r">{{ libelle(r) }}</option> }
                  </select>
                </td>
                <td>
                  {{ c.actif ? 'Actif' : 'Désactivé' }}
                  @if (c.invitationEnAttente) { · invitation en attente }
                  @else if (!c.secondFacteurActif) { · double authentification à activer }
                  @else { · {{ c.codesSecoursRestants }} codes de secours }
                </td>
                <td>
                  <div class="adm-actions">
                    @if (c.invitationEnAttente) {
                      <button biBouton type="button" variante="secondaire" taille="sm" (click)="agir(c, 'renvoyer-invitation', 'Invitation renvoyée.')">Renvoyer</button>
                    }
                    @if (c.secondFacteurActif) {
                      <button biBouton type="button" variante="secondaire" taille="sm" (click)="agir(c, 'reinitialiser-second-facteur', 'Double authentification réinitialisée.')">Réinitialiser 2FA</button>
                    }
                    @if (c.actif) {
                      <button biBouton type="button" variante="danger" taille="sm" (click)="agir(c, 'desactiver', 'Compte désactivé.')">Désactiver</button>
                    } @else {
                      <button biBouton type="button" variante="secondaire" taille="sm" (click)="agir(c, 'activer', 'Compte réactivé.')">Réactiver</button>
                    }
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
})
export class Utilisateurs implements OnInit {
  private readonly api = inject(ApiAdminService);
  protected readonly roles: RoleEquipe[] = ['SERVICE_CLIENT', 'GESTIONNAIRE_STOCK', 'ADMIN', 'SUPER_ADMIN'];
  protected readonly comptes = signal<CompteEquipe[]>([]);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly envoi = signal(false);
  protected invitation = { email: '', prenom: '', nom: '', role: 'SERVICE_CLIENT' as RoleEquipe };

  constructor() {
    inject(SeoService).definirPage({ titre: 'Équipe', indexable: false });
  }

  ngOnInit(): void {
    void this.charger();
  }

  protected libelle(role: RoleEquipe): string {
    return LIBELLES_ROLES[role];
  }

  private async charger(): Promise<void> {
    try {
      this.comptes.set(await this.api.get<CompteEquipe[]>('/api/admin/utilisateurs'));
    } catch (e) {
      this.erreur.set(lireErreur(e).message);
    }
  }

  protected async inviter(): Promise<void> {
    await this.executer(async () => {
      await this.api.post('/api/admin/utilisateurs', this.invitation);
      this.invitation = { email: '', prenom: '', nom: '', role: 'SERVICE_CLIENT' };
      return 'Invitation envoyée (lien valable 48 h).';
    });
  }

  protected async changerRole(c: CompteEquipe, role: RoleEquipe): Promise<void> {
    if (role === c.role) return;
    await this.executer(async () => {
      await this.api.patch(`/api/admin/utilisateurs/${c.id}/role`, { role });
      return 'Rôle modifié ; la personne devra se reconnecter.';
    });
  }

  protected async agir(c: CompteEquipe, action: string, succes: string): Promise<void> {
    await this.executer(async () => {
      await this.api.post(`/api/admin/utilisateurs/${c.id}/${action}`);
      return succes;
    });
  }

  private async executer(action: () => Promise<string>): Promise<void> {
    if (this.envoi()) return;
    this.envoi.set(true);
    this.erreur.set(null);
    this.message.set(null);
    try {
      this.message.set(await action());
    } catch (e) {
      this.erreur.set(lireErreur(e).message);
    } finally {
      this.envoi.set(false);
      await this.charger();
    }
  }
}
