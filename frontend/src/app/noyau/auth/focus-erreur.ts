import { Injector, afterNextRender } from '@angular/core';

/**
 * Place le focus sur le premier champ en erreur du formulaire (après le rendu des messages) et le ramène à l'écran :
 * un clavier de téléphone masque la moitié de la page, la personne ne doit pas chercher ce qui ne va pas.
 */
export function focaliserPremiereErreur(racine: HTMLElement, injector: Injector): void {
  afterNextRender(
    () => {
      const champ = racine.querySelector<HTMLElement>('input[aria-invalid="true"], select[aria-invalid="true"]');
      if (!champ) return;
      champ.focus({ preventScroll: true });
      champ.scrollIntoView({ block: 'center', behavior: 'auto' });
    },
    { injector },
  );
}
