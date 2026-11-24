import { Routes } from '@angular/router';

/**
 * Back-office (rendu côté client uniquement, voir app.routes.server.ts).
 * La garde par rôles et les écrans arrivent en phases 1 et 6.
 */
export const adminRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./tableau-de-bord/tableau-de-bord').then((m) => m.TableauDeBord),
  },
];
