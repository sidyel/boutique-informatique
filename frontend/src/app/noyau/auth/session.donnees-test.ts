import { signal } from '@angular/core';

import { Profil, SessionService } from './session.service';

/** Session factice pour les tests des services et pages qui ne s'intéressent pas à l'authentification. */
export function sessionDeTest(connecte = false): Partial<SessionService> & { prete: ReturnType<typeof signal<boolean>> } {
  const utilisateur = signal<Profil | null>(null);
  return {
    prete: signal(true),
    utilisateur,
    connecte: (() => connecte) as SessionService['connecte'],
    restaurer: () => Promise.resolve(),
    jetonAcces: () => null,
  };
}
