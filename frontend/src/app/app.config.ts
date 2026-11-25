import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import {
  ApplicationConfig,
  DEFAULT_CURRENCY_CODE,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import {
  provideClientHydration,
  withEventReplay,
  withIncrementalHydration,
} from '@angular/platform-browser';
import {
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
} from '@angular/router';

import { routes } from './app.routes';
import { fournirChargeurImages } from './noyau/images/chargeur-cloudinary';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
    ),
    // Hydratation incrémentale : les blocs @defer (hydrate on …) restent rendus par le serveur, mais leur
    // JavaScript n'est chargé et exécuté qu'au besoin (moins de travail au démarrage sur un petit Android).
    provideClientHydration(withEventReplay(), withIncrementalHydration()),
    // Le transfer cache (activé par provideClientHydration) réutilise dans le navigateur les réponses
    // obtenues pendant le SSR : voir BackendServeur pour la réécriture des URL côté serveur
    provideHttpClient(withFetch()),
    // NgOptimizedImage : largeur adaptée à l'écran (srcset) sur les URL Cloudinary
    fournirChargeurImages(),
    { provide: LOCALE_ID, useValue: 'fr' },
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'XOF' },
  ],
};
