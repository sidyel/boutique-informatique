import { Routes } from '@angular/router';

import { gardeEquipe, gardeRoles } from './noyau/garde-equipe';

/**
 * Back-office (rendu côté client uniquement, voir app.routes.server.ts). Hors cadre : connexion de l'équipe et invitation.
 * Dans le cadre : garde « session d'équipe » ; équipe et journal réservés au SUPER_ADMIN (le serveur refuse de toute façon).
 */
export const adminRoutes: Routes = [
  {
    path: 'connexion',
    loadComponent: () => import('./connexion/connexion-equipe').then((m) => m.ConnexionEquipe),
  },
  {
    path: 'invitation',
    loadComponent: () => import('./connexion/invitation').then((m) => m.Invitation),
  },
  {
    path: '',
    canActivate: [gardeEquipe],
    loadComponent: () => import('./cadre/cadre-admin').then((m) => m.CadreAdmin),
    children: [
      {
        path: '',
        loadComponent: () => import('./tableau-de-bord/tableau-de-bord').then((m) => m.TableauDeBord),
      },
      {
        path: 'utilisateurs',
        canActivate: [gardeRoles('SUPER_ADMIN')],
        loadComponent: () => import('./utilisateurs/utilisateurs').then((m) => m.Utilisateurs),
      },
      {
        path: 'journal',
        canActivate: [gardeRoles('SUPER_ADMIN')],
        loadComponent: () => import('./journal/journal').then((m) => m.Journal),
      },
      {
        path: 'mon-compte',
        loadComponent: () => import('./mon-compte/mon-compte').then((m) => m.MonCompteAdmin),
      },
    ],
  },
];
