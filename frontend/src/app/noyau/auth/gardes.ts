import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SessionService } from './session.service';

/**
 * Zone réservée : attend la restauration de la session, puis laisse passer ou renvoie vers la connexion en gardant
 * la page demandée (`?retour=`). Au rendu serveur il n'y a pas de session : ces routes sont rendues dans le navigateur.
 * (`inject` s'appelle avant tout `await` : après, le contexte d'injection n'existe plus.)
 */
export const gardeConnecte: CanActivateFn = async (_route, etat) => {
  const session = inject(SessionService);
  const router = inject(Router);
  await session.restaurer();
  if (session.connecte()) return true;
  return router.createUrlTree(['/connexion'], { queryParams: { retour: etat.url } });
};

/** Pages de connexion et d'inscription : inutiles à quelqu'un de déjà connecté. */
export const gardeVisiteur: CanActivateFn = async () => {
  const session = inject(SessionService);
  const router = inject(Router);
  await session.restaurer();
  return session.connecte() ? router.createUrlTree(['/compte']) : true;
};
