import { Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params } from '@angular/router';
import { map } from 'rxjs';

/** Paramètres d'URL de la route courante, en signal (valeur initiale synchrone : pas de rendu vide). */
export function parametresRoute(): Signal<Params> {
  const route = inject(ActivatedRoute);
  return toSignal(route.queryParams, { initialValue: route.snapshot.queryParams });
}

/**
 * Paramètre de chemin (`:slug`) en signal, lu sur la route plutôt que par une entrée de composant :
 * il est connu dès le constructeur, ce qui permet d'afficher tout de suite les réponses en mémoire.
 */
export function parametreChemin(nom: string): Signal<string> {
  const route = inject(ActivatedRoute);
  return toSignal(route.paramMap.pipe(map((p) => p.get(nom) ?? '')), {
    initialValue: route.snapshot.paramMap.get(nom) ?? '',
  });
}
