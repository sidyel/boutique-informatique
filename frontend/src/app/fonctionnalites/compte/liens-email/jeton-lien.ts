import { DestroyRef, inject } from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';

/**
 * Pages ouvertes par un lien d'e-mail (vérification, réinitialisation, déverrouillage) : le jeton est dans l'adresse.
 * - Aucune adresse de page n'est transmise à un site tiers : `Referrer-Policy: no-referrer` (en-tête nginx, ADR 0003 §12)
 *   et, en renfort, la balise `<meta name="referrer">` posée ici le temps de la page.
 * - Le jeton est lu une fois puis **retiré de la barre d'adresse** (historique, partage d'écran, favori) ; il est renvoyé
 *   au serveur en POST, jamais en paramètre d'URL.
 */
export function lireJetonDuLien(): string | null {
  const route = inject(ActivatedRoute);
  const router = inject(Router);
  const meta = inject(Meta);
  const jeton = route.snapshot.queryParamMap.get('jeton');

  const balise = meta.addTag({ name: 'referrer', content: 'no-referrer' }, true);
  inject(DestroyRef).onDestroy(() => balise?.remove());

  if (jeton !== null) {
    void router.navigate([], { queryParams: { jeton: null }, queryParamsHandling: 'merge', replaceUrl: true });
  }
  return jeton && jeton.trim() ? jeton.trim() : null;
}
