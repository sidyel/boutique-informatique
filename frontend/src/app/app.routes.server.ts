import { RenderMode, ServerRoute } from '@angular/ssr';

/**
 * Mode de rendu par route.
 * - Pages publiques : rendues côté serveur à chaque requête (SEO, données à jour).
 * - Administration : rendue uniquement dans le navigateur (aucun intérêt SEO, données privées).
 * Le prérendu des pages statiques (CGV, à propos…) sera ajouté en phase 3.
 */
export const serverRoutes: ServerRoute[] = [
  { path: 'admin', renderMode: RenderMode.Client },
  { path: 'admin/**', renderMode: RenderMode.Client },
  // Espace client et liens d'e-mail : privés (jeton en mémoire dans le navigateur), rien à indexer
  { path: 'compte', renderMode: RenderMode.Client },
  { path: 'compte/**', renderMode: RenderMode.Client },
  { path: '**', renderMode: RenderMode.Server },
];
