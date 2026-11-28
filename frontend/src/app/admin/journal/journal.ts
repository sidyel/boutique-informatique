import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { lireErreur } from '../../noyau/auth/erreurs-api';
import { SeoService } from '../../noyau/seo/seo.service';
import { Bouton } from '../../partage/composants/bouton/bouton';
import { ApiAdminService } from '../noyau/api-admin.service';

interface Ligne {
  id: number;
  date: string;
  utilisateurEmail: string | null;
  role: string | null;
  action: string;
  entiteType: string | null;
  entiteId: string | null;
  avant: string | null;
  apres: string | null;
  details: string | null;
  adresseIp: string | null;
  methode: string | null;
  chemin: string | null;
  statutHttp: number | null;
  generique: boolean;
}
interface PageJournal {
  lignes: Ligne[];
  total: number;
  page: number;
  taille: number;
}

/** Journal des actions d'administration (SUPER_ADMIN, lecture seule, conservé 3 ans). */
@Component({
  selector: 'bi-journal',
  imports: [FormsModule, DatePipe, Bouton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin.scss',
  template: `
    <section class="conteneur adm-page">
      <h1 class="adm-titre">Journal des actions</h1>
      <form class="adm-actions" (ngSubmit)="filtrer()" style="margin-bottom: var(--espace-4)">
        <input class="adm-saisie" name="auteur" type="search" inputmode="search" placeholder="Auteur (e-mail)" aria-label="Auteur" [(ngModel)]="auteur" />
        <input class="adm-saisie" name="action" placeholder="Action (ex. AVOIR_EMIS)" aria-label="Action" [(ngModel)]="action" />
        <button biBouton type="submit" variante="secondaire">Filtrer</button>
      </form>
      @if (erreur()) { <p class="adm-erreur" role="alert">{{ erreur() }}</p> }
      <div class="adm-tableau-conteneur">
        <table class="adm-tableau">
          <thead><tr><th>Date</th><th>Auteur</th><th>Action</th><th>Objet</th><th>Détail</th></tr></thead>
          <tbody>
            @for (l of page()?.lignes ?? []; track l.id) {
              <tr>
                <td class="adm-mono">{{ l.date | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                <td>{{ l.utilisateurEmail }}<br /><small>{{ l.role }} · {{ l.adresseIp }}</small></td>
                <td class="adm-mono">{{ l.action }}</td>
                <td>{{ l.entiteType }} {{ l.entiteId }}</td>
                <td>
                  @if (l.generique) { <small>{{ l.methode }} {{ l.chemin }} → {{ l.statutHttp }}</small> }
                  @if (l.avant || l.apres) { <small class="adm-mono">{{ l.avant }} → {{ l.apres }}</small> }
                  @if (l.details) { <small class="adm-mono">{{ l.details }}</small> }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <div class="adm-actions" style="margin-top: var(--espace-4)">
        <button biBouton type="button" variante="secondaire" [disabled]="numero() === 0" (click)="aller(numero() - 1)">Précédent</button>
        <span style="align-self: center">Page {{ numero() + 1 }} · {{ page()?.total ?? 0 }} lignes</span>
        <button biBouton type="button" variante="secondaire" [disabled]="!suivante()" (click)="aller(numero() + 1)">Suivant</button>
      </div>
    </section>
  `,
})
export class Journal implements OnInit {
  private readonly api = inject(ApiAdminService);
  protected readonly page = signal<PageJournal | null>(null);
  protected readonly numero = signal(0);
  protected readonly erreur = signal<string | null>(null);
  protected auteur = '';
  protected action = '';

  constructor() {
    inject(SeoService).definirPage({ titre: 'Journal', indexable: false });
  }

  ngOnInit(): void {
    void this.charger();
  }

  protected suivante(): boolean {
    const p = this.page();
    return !!p && (p.page + 1) * p.taille < p.total;
  }

  protected filtrer(): void {
    this.numero.set(0);
    void this.charger();
  }

  protected aller(n: number): void {
    this.numero.set(Math.max(0, n));
    void this.charger();
  }

  private async charger(): Promise<void> {
    const params: Record<string, string> = { page: String(this.numero()) };
    if (this.auteur.trim()) params['auteur'] = this.auteur.trim();
    if (this.action.trim()) params['action'] = this.action.trim();
    try {
      this.page.set(await this.api.get<PageJournal>('/api/admin/journal', params));
      this.erreur.set(null);
    } catch (e) {
      this.erreur.set(lireErreur(e).message);
    }
  }
}
