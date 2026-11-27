import { Routes } from '@angular/router';

import { gardeConnecte, gardeVisiteur } from './noyau/auth/gardes';
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
        path: 'produit/:slug',
        loadComponent: () =>
          import('./fonctionnalites/fiche-produit/page-fiche-produit').then((m) => m.PageFicheProduit),
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
        path: 'compte-supprime',
        loadComponent: () =>
          import('./fonctionnalites/compte/compte-supprime/compte-supprime').then((m) => m.CompteSupprime),
      },
      {
        path: 'connexion',
        canActivate: [gardeVisiteur],
        loadComponent: () => import('./fonctionnalites/compte/connexion/connexion').then((m) => m.Connexion),
      },
      {
        path: 'inscription',
        canActivate: [gardeVisiteur],
        loadComponent: () => import('./fonctionnalites/compte/inscription/inscription').then((m) => m.Inscription),
      },
      {
        path: 'mot-de-passe-oublie',
        loadComponent: () =>
          import('./fonctionnalites/compte/mot-de-passe-oublie/mot-de-passe-oublie').then((m) => m.MotDePasseOublie),
      },
      // Pages ouvertes par un lien d'e-mail : jeton renvoyé en POST, jamais de Referer (nginx + balise meta)
      {
        path: 'compte/verifier-email',
        data: { action: 'verifier-email' },
        loadComponent: () =>
          import('./fonctionnalites/compte/liens-email/page-lien-email').then((m) => m.PageLienEmail),
      },
      {
        path: 'compte/deverrouiller',
        data: { action: 'deverrouiller' },
        loadComponent: () =>
          import('./fonctionnalites/compte/liens-email/page-lien-email').then((m) => m.PageLienEmail),
      },
      {
        path: 'compte/confirmer-email',
        data: { action: 'confirmer-email' },
        loadComponent: () =>
          import('./fonctionnalites/compte/liens-email/page-lien-email').then((m) => m.PageLienEmail),
      },
      {
        path: 'compte/signaler-changement',
        data: { action: 'signaler-changement' },
        loadComponent: () =>
          import('./fonctionnalites/compte/liens-email/page-lien-email').then((m) => m.PageLienEmail),
      },
      {
        path: 'compte/nouveau-mot-de-passe',
        loadComponent: () =>
          import('./fonctionnalites/compte/liens-email/nouveau-mot-de-passe').then((m) => m.NouveauMotDePasse),
      },
      {
        path: 'compte',
        canActivate: [gardeConnecte],
        loadComponent: () => import('./fonctionnalites/compte/espace/espace-client').then((m) => m.EspaceClient),
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./fonctionnalites/compte/tableau-de-bord/tableau-de-bord').then((m) => m.TableauDeBord),
          },
          {
            path: 'commandes',
            loadComponent: () =>
              import('./fonctionnalites/compte/commandes/liste-commandes').then((m) => m.ListeCommandes),
          },
          {
            path: 'commandes/:numero',
            loadComponent: () =>
              import('./fonctionnalites/compte/commandes/detail-commande').then((m) => m.DetailCommande),
          },
          {
            path: 'avis',
            loadComponent: () => import('./fonctionnalites/compte/avis/liste-avis').then((m) => m.ListeAvis),
          },
          { path: 'profil', loadComponent: () => import('./fonctionnalites/compte/profil/profil').then((m) => m.Profil) },
          {
            path: 'adresses',
            loadComponent: () => import('./fonctionnalites/compte/adresses/adresses').then((m) => m.Adresses),
          },
          {
            path: 'securite',
            loadComponent: () => import('./fonctionnalites/compte/securite/securite').then((m) => m.Securite),
          },
        ],
      },
      {
        path: 'panier',
        loadComponent: () =>
          import('./fonctionnalites/panier/page-panier/page-panier').then((m) => m.PagePanier),
      },
      {
        path: 'commande',
        loadComponent: () =>
          import('./fonctionnalites/commande/page-commande/page-commande').then((m) => m.PageCommande),
      },
      {
        path: 'commande/retour/:jeton',
        loadComponent: () =>
          import('./fonctionnalites/commande/page-retour-paiement/page-retour-paiement').then(
            (m) => m.PageRetourPaiement,
          ),
      },
      {
        path: 'suivi/:jeton',
        loadComponent: () =>
          import('./fonctionnalites/commande/page-suivi/page-suivi').then((m) => m.PageSuivi),
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
