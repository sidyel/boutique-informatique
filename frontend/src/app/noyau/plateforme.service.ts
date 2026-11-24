import { isPlatformBrowser, isPlatformServer } from '@angular/common';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';

/**
 * Point unique pour savoir où s'exécute le code.
 * Règle du projet : aucun accès à window, document global, localStorage ou aux scripts
 * de suivi pendant le rendu serveur. Utiliser `estNavigateur` ou `afterNextRender`.
 */
@Injectable({ providedIn: 'root' })
export class PlateformeService {
  private readonly idPlateforme = inject(PLATFORM_ID);

  readonly estNavigateur = isPlatformBrowser(this.idPlateforme);
  readonly estServeur = isPlatformServer(this.idPlateforme);
}
