import { Routes } from '@angular/router';

import { MiseEnPagePublique } from './mise-en-page/mise-en-page-publique';

/**
 * Pages publiques dans leur cadre (en-tête, pied de page), chacune chargée à la demande.
 * L'administration a son propre cadre (rendu dans le navigateur uniquement).
 */
export const routes: Routes = [
  {
    path: 'admin',
    loadChildren: () => import('./admin/admin.routes').then((m) => m.adminRoutes),
  },
  {
    path: '',
    component: MiseEnPagePublique,
    children: [
      {
        path: '',
        loadComponent: () => import('./fonctionnalites/accueil/accueil').then((m) => m.Accueil),
      },
      {
        path: 'categorie/:slug',
        loadComponent: () =>
          import('./fonctionnalites/categorie/page-categorie').then((m) => m.PageCategorie),
      },
      {
        path: 'marque/:slug',
        loadComponent: () => import('./fonctionnalites/marque/page-marque').then((m) => m.PageMarque),
      },
      {
        path: 'recherche',
        loadComponent: () =>
          import('./fonctionnalites/recherche/page-recherche').then((m) => m.PageRecherche),
      },
      {
        path: 'guide-style',
        loadComponent: () =>
          import('./fonctionnalites/guide-style/guide-style').then((m) => m.GuideStyle),
      },
      {
        path: 'compte',
        loadComponent: () =>
          import('./fonctionnalites/page-a-venir/page-a-venir').then((m) => m.PageAVenir),
        data: {
          titre: 'Mon compte',
          texte:
            'Le compte client (suivi des commandes, adresses, factures) sera bientôt disponible. ' +
            'En attendant, vous pouvez commander par WhatsApp ou par téléphone.',
        },
      },
      {
        path: 'panier',
        loadComponent: () =>
          import('./fonctionnalites/page-a-venir/page-a-venir').then((m) => m.PageAVenir),
        data: {
          titre: 'Mon panier',
          texte:
            'Votre panier est vide. La commande en ligne sera bientôt disponible : ' +
            'en attendant, commandez par WhatsApp ou par téléphone.',
        },
      },
      {
        path: '**',
        loadComponent: () =>
          import('./fonctionnalites/erreurs/page-introuvable/page-introuvable').then(
            (m) => m.PageIntrouvable,
          ),
      },
    ],
  },
];
