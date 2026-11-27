import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/** Coque de l'espace client : navigation (défilante sur mobile, latérale sur grand écran) et contenu. */
@Component({
  selector: 'bi-espace-client',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../espace.scss',
  template: `
    <div class="conteneur espace">
      <nav class="espace__nav" aria-label="Mon compte">
        <a routerLink="/compte" routerLinkActive="actif" [routerLinkActiveOptions]="{ exact: true }">Aperçu</a>
        <a routerLink="/compte/profil" routerLinkActive="actif">Profil</a>
        <a routerLink="/compte/adresses" routerLinkActive="actif">Adresses</a>
        <a routerLink="/compte/securite" routerLinkActive="actif">Sécurité</a>
      </nav>
      <div class="espace__contenu"><router-outlet /></div>
    </div>
  `,
})
export class EspaceClient {}
