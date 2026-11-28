import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SessionService } from '../../noyau/auth/session.service';

const ROLES_EQUIPE = ['SERVICE_CLIENT', 'GESTIONNAIRE_STOCK', 'ADMIN', 'SUPER_ADMIN'];

/** Zone d'administration : une session d'équipe (second facteur déjà passé), sinon la page de connexion de l'équipe. */
export const gardeEquipe: CanActivateFn = async (_route, etat) => {
  const session = inject(SessionService);
  const router = inject(Router);
  await session.restaurer();
  const role = session.utilisateur()?.role;
  if (role && ROLES_EQUIPE.includes(role)) return true;
  return router.createUrlTree(['/admin/connexion'], { queryParams: { retour: etat.url } });
};

/** Écrans réservés à certains rôles : les autres reviennent au tableau de bord. */
export function gardeRoles(...roles: string[]): CanActivateFn {
  return async () => {
    const session = inject(SessionService);
    const router = inject(Router);
    await session.restaurer();
    const role = session.utilisateur()?.role;
    return role && roles.includes(role) ? true : router.createUrlTree(['/admin']);
  };
}
