import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { lireErreur } from '../../noyau/auth/erreurs-api';
import { SeoService } from '../../noyau/seo/seo.service';
import { PlateformeService } from '../../noyau/plateforme.service';
import { Bouton } from '../../partage/composants/bouton/bouton';
import { ApiAdminService, LIBELLES_ROLES, Moi } from '../noyau/api-admin.service';

/** Compte de l'équipe : rôle, codes de secours restants, régénération (mot de passe + code TOTP, nouvelle liste à confirmer). */
@Component({
  selector: 'bi-mon-compte-admin',
  imports: [FormsModule, Bouton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin.scss',
  template: `
    <section class="conteneur adm-page" style="max-width: 36rem">
      <h1 class="adm-titre">Mon compte</h1>
      @if (moi(); as m) {
        <p>{{ m.prenom }} {{ m.nom }} · <span class="adm-mono">{{ m.email }}</span><br />Rôle : {{ libelleRole(m.role) }}</p>
        <p>Double authentification : <strong>{{ m.secondFacteurActif ? 'activée' : 'non activée' }}</strong> ·
          {{ m.codesSecoursRestants }} code(s) de secours restant(s).</p>

        @if (codes().length) {
          <p class="adm-alerte" role="status">Nouveaux codes de secours : les anciens ne fonctionnent plus. Ils ne seront plus jamais affichés.</p>
          <ul class="adm-codes-secours">@for (c of codes(); track c) { <li>{{ c }}</li> }</ul>
          <div class="adm-actions"><button biBouton type="button" variante="secondaire" (click)="imprimer()">Imprimer</button></div>
          <label class="adm-case"><input type="checkbox" name="ok" [(ngModel)]="imprimes" /><span>Je les ai imprimés ou notés en lieu sûr.</span></label>
          <button biBouton type="button" [disabled]="!imprimes" [chargement]="envoi()" (click)="confirmer()">Confirmer</button>
        } @else {
          <form class="adm-carte adm-formulaire" (ngSubmit)="regenerer()">
            <h2 style="margin: 0; font-size: var(--taille-lg)">Régénérer les codes de secours</h2>
            <label class="adm-etiquette">Mot de passe
              <input class="adm-saisie" name="mdp" type="password" autocomplete="current-password" required [(ngModel)]="motDePasse" />
            </label>
            <label class="adm-etiquette">Code à 6 chiffres
              <input class="adm-saisie adm-code" name="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" required [(ngModel)]="code" />
            </label>
            <button biBouton type="submit" [chargement]="envoi()">Générer 10 nouveaux codes</button>
          </form>
        }
      }
      @if (message()) { <p class="adm-info" role="status">{{ message() }}</p> }
      @if (erreur()) { <p class="adm-erreur" role="alert">{{ erreur() }}</p> }
    </section>
  `,
})
export class MonCompteAdmin implements OnInit {
  private readonly api = inject(ApiAdminService);
  private readonly navigateur = inject(PlateformeService).estNavigateur;
  protected readonly moi = signal<Moi | null>(null);
  protected readonly codes = signal<string[]>([]);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly envoi = signal(false);
  protected motDePasse = '';
  protected code = '';
  protected imprimes = false;

  constructor() {
    inject(SeoService).definirPage({ titre: 'Mon compte', indexable: false });
  }

  ngOnInit(): void {
    void this.charger();
  }

  protected libelleRole(role: Moi['role']): string {
    return LIBELLES_ROLES[role];
  }

  private async charger(): Promise<void> {
    this.moi.set(await this.api.get<Moi>('/api/admin/moi'));
  }

  protected async regenerer(): Promise<void> {
    await this.executer(async () => {
      const r = await this.api.post<{ codesSecours: string[] }>('/api/admin/moi/codes-secours', {
        motDePasse: this.motDePasse,
        code: this.code.trim(),
      });
      this.motDePasse = '';
      this.code = '';
      this.imprimes = false;
      this.codes.set(r.codesSecours);
    });
  }

  protected async confirmer(): Promise<void> {
    await this.executer(async () => {
      await this.api.post('/api/admin/moi/codes-secours/confirmer');
      this.codes.set([]);
      this.message.set('Nouveaux codes de secours confirmés.');
      await this.charger();
    });
  }

  protected imprimer(): void {
    if (this.navigateur) window.print();
  }

  private async executer(action: () => Promise<void>): Promise<void> {
    if (this.envoi()) return;
    this.envoi.set(true);
    this.erreur.set(null);
    this.message.set(null);
    try {
      await action();
    } catch (e) {
      this.erreur.set(lireErreur(e).message);
    } finally {
      this.envoi.set(false);
    }
  }
}
