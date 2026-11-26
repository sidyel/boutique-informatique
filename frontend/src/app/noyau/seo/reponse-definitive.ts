import { RESPONSE_INIT, Signal, computed, effect, inject, untracked } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Lien } from '../api/modeles';
import { chargementMemorise } from '../donnees/chargement-memorise';

/** Réponse de `/api/redirections` (ADR 0007). */
export interface RedirectionReponse {
  /** Chemin actuel pour une 301 ; null pour une 410. */
  destination: string | null;
  statut: 301 | 410;
  retrait: { nom: string; categorie: Lien | null } | null;
}

export interface ReponseDefinitive {
  /** Vraie 404 : ni ancien slug, ni retrait définitif (ou résolution impossible). */
  readonly introuvable: Signal<boolean>;
  /** Retrait définitif (410) : ce qui a été retiré, pour la page « n'est plus vendu ». */
  readonly retrait: Signal<RedirectionReponse['retrait']>;
}

/**
 * Quand l'API répond 404 pour une page publique (produit, catégorie, marque), demande au backend si
 * l'adresse a une réponse définitive :
 * - 301 : pendant le rendu serveur, le statut est posé et le routeur navigue vers la nouvelle adresse ;
 *   Angular SSR répond alors « 301 + Location » sans corps. Dans le navigateur : navigation qui remplace
 *   l'entrée d'historique. Les paramètres de la requête sont conservés (filtres, variante).
 * - 410 : statut posé pendant le rendu serveur, la page affiche le retrait.
 * - sinon : vraie 404 (page introuvable).
 *
 * @param chemin chemin de la page introuvable (« /produit/slug »), ou null tant que l'API n'a pas répondu 404
 */
export function reponseDefinitive(chemin: () => string | null): ReponseDefinitive {
  const router = inject(Router);
  const route = inject(ActivatedRoute);
  const reponseInit = inject(RESPONSE_INIT, { optional: true });
  const resolution = chargementMemorise<RedirectionReponse>(() => {
    const c = chemin();
    return c ? `/api/redirections?chemin=${encodeURIComponent(c)}` : null;
  });

  // La réponse doit correspondre au chemin demandé (pas à celle, gardée pendant le chargement, du précédent)
  const reponse = computed(() =>
    chemin() && !resolution.chargement() && !resolution.erreur() ? resolution.valeur() : undefined,
  );

  effect(() => {
    const r = reponse();
    if (!r) return;
    if (r.statut === 301 && r.destination) {
      if (reponseInit) reponseInit.status = 301;
      const destination = r.destination;
      untracked(() =>
        void router.navigateByUrl(
          router.createUrlTree([destination], { queryParams: route.snapshot.queryParams }),
          { replaceUrl: true },
        ),
      );
    } else if (r.statut === 410 && reponseInit) {
      reponseInit.status = 410;
      reponseInit.statusText = 'Gone';
    }
  });

  return {
    // 404 du backend (rien à appliquer), ou résolution impossible : page introuvable plutôt qu'une page vide
    introuvable: computed(() => chemin() !== null && !resolution.chargement() && resolution.statutErreur() !== undefined),
    retrait: computed(() => {
      const r = reponse();
      return r?.statut === 410 ? r.retrait : null;
    }),
  };
}
