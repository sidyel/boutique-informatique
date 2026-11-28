import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { lireErreur } from '../../noyau/auth/erreurs-api';
import {
  EtapeEquipe,
  ReponseConnexion,
  ReponseEtape,
  SessionService,
} from '../../noyau/auth/session.service';
import { PlateformeService } from '../../noyau/plateforme.service';
import { SeoService } from '../../noyau/seo/seo.service';
import { Bouton } from '../../partage/composants/bouton/bouton';

interface Enrolement {
  secret: string;
  uri: string;
  codeQr: string;
}
interface ReponseCodes {
  etape: EtapeEquipe;
  jetonEtape: string;
  codesSecours: string[];
}

type Ecran = 'mot-de-passe' | 'enrolement' | 'code' | 'codes-secours';

/**
 * Connexion de l'équipe (ADR 0009) : mot de passe, puis code TOTP (ou code de secours) ou enrôlement imposé
 * (QR code, puis codes de secours à imprimer et à confirmer). Aucune session n'existe avant la dernière étape.
 */
@Component({
  selector: 'bi-connexion-equipe',
  imports: [FormsModule, RouterLink, Bouton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin.scss',
  template: `
    <section class="conteneur adm-page" style="max-width: 28rem">
      <h1 class="adm-titre">Connexion à l'administration</h1>

      @switch (ecran()) {
        @case ('mot-de-passe') {
          <form class="adm-formulaire" (ngSubmit)="envoyerMotDePasse()">
            <label class="adm-etiquette">Adresse e-mail
              <input class="adm-saisie" name="email" type="email" autocomplete="username" inputmode="email" required [(ngModel)]="email" />
            </label>
            <label class="adm-etiquette">Mot de passe
              <input class="adm-saisie" name="motDePasse" type="password" autocomplete="current-password" required [(ngModel)]="motDePasse" />
            </label>
            @if (erreur()) { <p class="adm-erreur" role="alert">{{ erreur() }}</p> }
            <button biBouton type="submit" pleineLargeur [chargement]="envoi()">Continuer</button>
          </form>
          <p><a routerLink="/mot-de-passe-oublie">Mot de passe oublié ?</a></p>
        }

        @case ('enrolement') {
          <p>Votre compte exige une double authentification. Scannez ce code avec une application d'authentification
            (Google Authenticator, Microsoft Authenticator, Aegis…), puis saisissez le code à 6 chiffres affiché.</p>
          @if (enrolement(); as e) {
            <img [src]="e.codeQr" width="200" height="200" alt="Code QR à scanner avec l'application d'authentification" />
            <details>
              <summary>Saisir la clé à la main</summary>
              <p class="adm-mono" style="overflow-wrap: anywhere">{{ e.secret }}</p>
            </details>
          }
          <form class="adm-formulaire" (ngSubmit)="confirmerEnrolement()">
            <label class="adm-etiquette">Code à 6 chiffres
              <input class="adm-saisie adm-code" name="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" required [(ngModel)]="code" />
            </label>
            @if (erreur()) { <p class="adm-erreur" role="alert">{{ erreur() }}</p> }
            <button biBouton type="submit" pleineLargeur [chargement]="envoi()">Activer</button>
          </form>
        }

        @case ('code') {
          <form class="adm-formulaire" (ngSubmit)="verifierCode()">
            @if (!codeSecours()) {
              <label class="adm-etiquette">Code de votre application
                <input class="adm-saisie adm-code" name="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" required [(ngModel)]="code" />
              </label>
            } @else {
              <label class="adm-etiquette">Code de secours (XXXXX-XXXXX)
                <input class="adm-saisie adm-code" name="codeSecours" autocomplete="off" autocapitalize="characters" required [(ngModel)]="code" />
              </label>
            }
            @if (erreur()) { <p class="adm-erreur" role="alert">{{ erreur() }}</p> }
            <button biBouton type="submit" pleineLargeur [chargement]="envoi()">Se connecter</button>
            <button biBouton type="button" variante="fantome" (click)="basculerCodeSecours()">
              {{ codeSecours() ? 'Utiliser mon application' : 'Utiliser un code de secours' }}
            </button>
          </form>
        }

        @case ('codes-secours') {
          <p class="adm-alerte" role="status">
            Ces 10 codes permettent d'entrer si vous perdez votre téléphone. Ils ne seront <strong>plus jamais affichés</strong>.
            Imprimez-les ou notez-les, et rangez-les à part de votre mot de passe.
          </p>
          <ul class="adm-codes-secours">
            @for (c of codesSecours(); track c) { <li>{{ c }}</li> }
          </ul>
          <div class="adm-actions">
            <button biBouton type="button" variante="secondaire" (click)="imprimer()">Imprimer</button>
          </div>
          <label class="adm-case">
            <input type="checkbox" [(ngModel)]="imprimes" name="imprimes" />
            <span>Je les ai imprimés ou notés en lieu sûr.</span>
          </label>
          @if (erreur()) { <p class="adm-erreur" role="alert">{{ erreur() }}</p> }
          <button biBouton type="button" pleineLargeur [disabled]="!imprimes" [chargement]="envoi()" (click)="confirmerCodes()">
            Terminer la connexion
          </button>
        }
      }
    </section>
  `,
})
export class ConnexionEquipe implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly seo = inject(SeoService);
  private readonly navigateur = inject(PlateformeService).estNavigateur;

  protected readonly ecran = signal<Ecran>('mot-de-passe');
  protected readonly erreur = signal<string | null>(null);
  protected readonly envoi = signal(false);
  protected readonly enrolement = signal<Enrolement | null>(null);
  protected readonly codesSecours = signal<string[]>([]);
  protected readonly codeSecours = signal(false);

  protected email = '';
  protected motDePasse = '';
  protected code = '';
  protected imprimes = false;
  private jetonEtape = '';

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Connexion à l’administration', indexable: false });
    // Arrivée depuis la page de connexion publique : le mot de passe est déjà vérifié
    const etat = this.navigateur ? (history.state as { etape?: ReponseEtape } | null) : null;
    if (etat?.etape) void this.suivre(etat.etape);
  }

  protected async envoyerMotDePasse(): Promise<void> {
    await this.executer(async () => {
      const reponse = await this.session.connecterAvecEtape(this.email.trim(), this.motDePasse);
      this.motDePasse = '';
      if ('etape' in reponse) await this.suivre(reponse);
      else await this.terminer(reponse);
    });
  }

  private async suivre(reponse: { etape: EtapeEquipe; jetonEtape: string }): Promise<void> {
    this.jetonEtape = reponse.jetonEtape;
    this.code = '';
    if (reponse.etape === 'ENROLEMENT_REQUIS') {
      const e = await firstValueFrom(
        this.http.post<Enrolement>('/api/auth/second-facteur/enrolement/demarrer', { jetonEtape: this.jetonEtape }),
      );
      this.enrolement.set(e);
      this.ecran.set('enrolement');
    } else if (reponse.etape === 'CODE_REQUIS') {
      this.ecran.set('code');
    } else {
      this.ecran.set('codes-secours');
    }
  }

  protected async confirmerEnrolement(): Promise<void> {
    await this.executer(async () => {
      const r = await firstValueFrom(
        this.http.post<ReponseCodes>('/api/auth/second-facteur/enrolement/confirmer', {
          jetonEtape: this.jetonEtape,
          code: this.code.trim(),
        }),
      );
      this.jetonEtape = r.jetonEtape;
      this.codesSecours.set(r.codesSecours);
      this.ecran.set('codes-secours');
    });
  }

  protected async verifierCode(): Promise<void> {
    await this.executer(async () => {
      const valeur = this.code.trim();
      const corps = this.codeSecours() ? { jetonEtape: this.jetonEtape, codeSecours: valeur } : { jetonEtape: this.jetonEtape, code: valeur };
      const r = await firstValueFrom(this.http.post<ReponseConnexion | ReponseCodes>('/api/auth/second-facteur/verifier', corps));
      if ('etape' in r) {
        this.jetonEtape = r.jetonEtape;
        this.codesSecours.set(r.codesSecours ?? []);
        this.ecran.set('codes-secours');
      } else {
        await this.terminer(r);
      }
    });
  }

  protected async confirmerCodes(): Promise<void> {
    if (!this.imprimes) return;
    await this.executer(async () => {
      const r = await firstValueFrom(
        this.http.post<ReponseConnexion>('/api/auth/second-facteur/codes-secours/confirmer', { jetonEtape: this.jetonEtape }),
      );
      await this.terminer(r);
    });
  }

  protected basculerCodeSecours(): void {
    this.codeSecours.update((v) => !v);
    this.code = '';
    this.erreur.set(null);
  }

  protected imprimer(): void {
    if (this.navigateur) window.print();
  }

  private async terminer(reponse: ReponseConnexion): Promise<void> {
    this.session.ouvrirSessionEquipe(reponse);
    const retour = this.route.snapshot.queryParamMap.get('retour');
    await this.router.navigateByUrl(retour && retour.startsWith('/admin') && !retour.startsWith('//') ? retour : '/admin');
  }

  private async executer(action: () => Promise<void>): Promise<void> {
    if (this.envoi()) return;
    this.envoi.set(true);
    this.erreur.set(null);
    try {
      await action();
    } catch (e) {
      const lue = lireErreur(e);
      if (lue.code === 'ETAPE_INVALIDE') {
        this.erreur.set('Votre connexion a expiré. Recommencez avec votre mot de passe.');
        this.ecran.set('mot-de-passe');
      } else {
        this.erreur.set(lue.message);
      }
    } finally {
      this.envoi.set(false);
    }
  }
}
