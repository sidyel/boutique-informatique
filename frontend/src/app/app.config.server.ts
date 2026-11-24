import { ApplicationConfig, mergeApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';

import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';
import { URL_API } from './noyau/api/url-api';

/**
 * Configuration propre au rendu serveur.
 * Pendant le SSR, HttpClient exige des URL absolues : on appelle le backend directement
 * (réseau Docker : http://backend:8080), sans repasser par nginx.
 */
const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(withRoutes(serverRoutes)),
    { provide: URL_API, useFactory: () => process.env['API_URL_SERVEUR'] ?? 'http://localhost:8080' },
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
