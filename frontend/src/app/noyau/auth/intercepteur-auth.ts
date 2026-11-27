import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, of, switchMap, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { SessionService } from './session.service';

/** Appels qui ne portent jamais le jeton d'accès : l'authentification elle-même (cookie de rafraîchissement). */
function appelAuthentification(url: string): boolean {
  return url.startsWith('/api/auth/');
}

function avecJeton(requete: HttpRequest<unknown>, jeton: string | null): HttpRequest<unknown> {
  return jeton ? requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : requete;
}

/**
 * Joint le jeton d'accès aux appels `/api/…` (navigateur seulement : le rendu serveur n'a jamais de session).
 * - Jeton sur le point d'expirer : renouvelé avant l'appel.
 * - Réponse 401 alors qu'une session existait : un rafraîchissement, puis un seul nouvel essai.
 */
export const intercepteurAuth: HttpInterceptorFn = (requete, suivant) => {
  const session = inject(SessionService);
  if (!requete.url.startsWith('/api/') || appelAuthentification(requete.url) || session.jetonAcces() === null) {
    return suivant(requete);
  }

  const preparer = session.expireBientot() ? from(session.rafraichir()) : of(true);
  return preparer.pipe(
    switchMap(() => suivant(avecJeton(requete, session.jetonAcces()))),
    catchError((erreur: unknown) => {
      if (erreur instanceof HttpErrorResponse && erreur.status === 401) {
        return from(session.rafraichir(true)).pipe(
          switchMap((ok) => (ok ? suivant(avecJeton(requete, session.jetonAcces())) : throwError(() => erreur))),
        );
      }
      return throwError(() => erreur);
    }),
  );
};
