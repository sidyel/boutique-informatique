import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { lireErreur } from '../../../noyau/auth/erreurs-api';
import { SessionService } from '../../../noyau/auth/session.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { lireJetonDuLien } from './jeton-lien';

type Action = 'verifier-email' | 'deverrouiller';

const TEXTES: Record<Action, { titre: string; chemin: string; ok: string; erreur: string }> = {
  'verifier-email': {
    titre: 'Vérification de l’adresse e-mail',
    chemin: '/api/auth/verification-email',
    ok: 'Votre adresse e-mail est vérifiée. Merci !',
    erreur: 'Ce lien n’est plus valide : il a expiré ou a déjà servi. Connectez-vous pour en demander un nouveau.',
  },
  deverrouiller: {
    titre: 'Déverrouillage du compte',
    chemin: '/api/auth/deverrouiller',
    ok: 'Votre compte est déverrouillé. Vous pouvez vous connecter.',
    erreur: 'Ce lien n’est plus valide : il a expiré ou a déjà servi. Utilisez « Mot de passe oublié » si besoin.',
  },
};

/** Page de confirmation ouverte par un lien d'e-mail : le jeton part en POST dès l'ouverture, une seule fois. */
@Component({
  selector: 'bi-page-lien-email',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../auth.scss',
  template: `
    <section class="conteneur auth">
      <h1 class="auth__titre">{{ textes.titre }}</h1>
      @switch (etat()) {
        @case ('attente') {
          <p class="auth__intro" role="status">Vérification en cours…</p>
        }
        @case ('ok') {
          <p class="auth__succes" role="status">{{ textes.ok }}</p>
        }
        @default {
          <p class="auth__erreur" role="alert">{{ message() }}</p>
        }
      }
      <nav class="auth__liens" aria-label="Suite">
        @if (session.connecte()) {
          <a routerLink="/compte">Mon compte</a>
        } @else {
          <a routerLink="/connexion">Se connecter</a>
        }
        <a routerLink="/">Retour à la boutique</a>
      </nav>
    </section>
  `,
})
export class PageLienEmail implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly seo = inject(SeoService);
  protected readonly session = inject(SessionService);
  private readonly action = inject(ActivatedRoute).snapshot.data['action'] as Action;
  private readonly jeton = lireJetonDuLien();

  protected readonly textes = TEXTES[this.action];
  protected readonly etat = signal<'attente' | 'ok' | 'erreur'>('attente');
  protected readonly message = signal(this.textes.erreur);

  ngOnInit(): void {
    this.seo.definirPage({ titre: this.textes.titre, indexable: false });
    void this.confirmer();
  }

  private async confirmer(): Promise<void> {
    if (!this.jeton) {
      this.etat.set('erreur');
      return;
    }
    try {
      await firstValueFrom(this.http.post(this.textes.chemin, { jeton: this.jeton }));
      // Le jeton d'accès en cours garde `emailVerifie=false` : on le renouvelle (ADR 0003 §12)
      await this.session.restaurer();
      if (this.session.connecte()) await this.session.rafraichir(true);
      this.etat.set('ok');
    } catch (e) {
      const lue = lireErreur(e);
      this.message.set(lue.code === 'JETON_INVALIDE' || lue.statut === 400 ? this.textes.erreur : lue.message);
      this.etat.set('erreur');
    }
  }
}
