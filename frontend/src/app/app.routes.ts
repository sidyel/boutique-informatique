import { Routes } from '@angular/router';

/**
 * Toutes les routes sont chargées à la demande (un fichier JS par page).
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./fonctionnalites/accueil/accueil').then((m) => m.Accueil),
  },
  {
    path: 'guide-style',
    loadComponent: () =>
      import('./fonctionnalites/guide-style/guide-style').then((m) => m.GuideStyle),
  },
  {
    path: 'admin',
    loadChildren: () => import('./admin/admin.routes').then((m) => m.adminRoutes),
  },
  {
    path: '**',
    loadComponent: () =>
      import('./fonctionnalites/erreurs/page-introuvable/page-introuvable').then(
        (m) => m.PageIntrouvable,
      ),
  },
];
