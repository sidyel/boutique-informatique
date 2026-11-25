import { InjectionToken } from '@angular/core';

/**
 * Préfixe ajouté aux appels « /api/… » par {@link BackendServeur} (rendu serveur uniquement).
 * - Navigateur : chaîne vide → URL relatives, servies par nginx (ou le proxy de ng serve).
 * - Serveur SSR : URL absolue du backend (variable d'environnement API_URL_SERVEUR).
 */
export const URL_API = new InjectionToken<string>('URL_API', {
  providedIn: 'root',
  factory: () => '',
});
