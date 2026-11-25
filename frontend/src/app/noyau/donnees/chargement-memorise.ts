import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import {
  DestroyRef,
  Injectable,
  PLATFORM_ID,
  Signal,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { Subscription } from 'rxjs';

/** Réponses gardées en mémoire, et leur durée de fraîcheur (au-delà : affichées puis relues). */
const ENTREES_MAX = 16;
export const FRAICHEUR_MS = 5 * 60_000;

/**
 * Dernières réponses de listes, dans le navigateur uniquement (jamais côté serveur, où un service racine
 * vit le temps d'une requête). Le retour arrière d'une fiche produit vers une liste trouve ici ses données
 * tout de suite : la liste est rendue avant que le routeur ne restaure la position de défilement, et le
 * client retrouve exactement le produit qu'il regardait, sans nouvel appel réseau.
 */
@Injectable({ providedIn: 'root' })
export class MemoireListes {
  private readonly entrees = new Map<string, { valeur: unknown; date: number }>();
  private readonly actif = isPlatformBrowser(inject(PLATFORM_ID));

  lire<T>(url: string): { valeur: T; fraiche: boolean } | undefined {
    const entree = this.entrees.get(url);
    return entree && { valeur: entree.valeur as T, fraiche: Date.now() - entree.date < FRAICHEUR_MS };
  }

  ecrire(url: string, valeur: unknown): void {
    if (!this.actif) return;
    this.entrees.delete(url);
    this.entrees.set(url, { valeur, date: Date.now() });
    if (this.entrees.size > ENTREES_MAX) {
      this.entrees.delete(this.entrees.keys().next().value!);
    }
  }
}

export interface ChargementMemorise<T> {
  /** Dernière valeur reçue : elle reste affichée pendant le chargement suivant (pas de saut de page). */
  readonly valeur: Signal<T | undefined>;
  readonly chargement: Signal<boolean>;
  readonly erreur: Signal<HttpErrorResponse | undefined>;
  /** Statut HTTP de la dernière erreur (404 : ressource introuvable). */
  readonly statutErreur: Signal<number | undefined>;
}

/**
 * Lecture GET suivant une URL réactive (`null` : rien à lire), mémorisée par {@link MemoireListes}.
 * Contrairement à `httpResource`, la valeur précédente reste disponible pendant le chargement suivant
 * et une valeur en mémoire est servie de façon synchrone dès la création. Passe par `HttpClient` :
 * transfer cache du rendu serveur et réécriture des URL côté serveur ({@link BackendServeur}) inchangés.
 * À appeler dans un contexte d'injection (constructeur ou champ de composant).
 */
export function chargementMemorise<T>(url: () => string | null): ChargementMemorise<T> {
  const http = inject(HttpClient);
  const memoire = inject(MemoireListes);
  const valeur = signal<T | undefined>(undefined);
  const chargement = signal(false);
  const erreur = signal<HttpErrorResponse | undefined>(undefined);
  let abonnement: Subscription | undefined;
  let derniereUrl: string | null | undefined;

  const charger = (adresse: string | null) => {
    if (adresse === derniereUrl) return;
    derniereUrl = adresse;
    abonnement?.unsubscribe();
    if (adresse === null) {
      chargement.set(false);
      return;
    }
    const enMemoire = memoire.lire<T>(adresse);
    if (enMemoire) {
      valeur.set(enMemoire.valeur);
      erreur.set(undefined);
      if (enMemoire.fraiche) {
        chargement.set(false);
        return;
      }
    }
    chargement.set(true);
    abonnement = http.get<T>(adresse).subscribe({
      next: (reponse) => {
        memoire.ecrire(adresse, reponse);
        valeur.set(reponse);
        erreur.set(undefined);
        chargement.set(false);
      },
      error: (e: HttpErrorResponse) => {
        erreur.set(e);
        chargement.set(false);
      },
    });
  };

  // Valeur en mémoire servie dès la création : le premier rendu l'affiche déjà
  untracked(() => charger(url()));
  effect(() => {
    const adresse = url();
    untracked(() => charger(adresse));
  });
  inject(DestroyRef).onDestroy(() => abonnement?.unsubscribe());

  return {
    valeur,
    chargement,
    erreur,
    statutErreur: computed(() => erreur()?.status),
  };
}
