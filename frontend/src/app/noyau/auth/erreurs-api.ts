import { HttpErrorResponse } from '@angular/common/http';

export interface ErreurLue {
  /** Message lisible, en français. */
  message: string;
  /** Erreurs de validation par champ (`ProblemDetail.erreurs`). */
  champs: Record<string, string>;
  /** Code applicatif (`VALIDATION`, `JETON_INVALIDE`, `TROP_DE_REQUETES`…). */
  code: string | null;
  statut: number;
}

const GENERIQUE = 'Une erreur est survenue. Réessayez dans un instant ou contactez-nous.';

/** Lit une erreur de l'API (ProblemDetail) pour l'afficher dans un formulaire. */
export function lireErreur(erreur: unknown): ErreurLue {
  if (!(erreur instanceof HttpErrorResponse)) return { message: GENERIQUE, champs: {}, code: null, statut: 0 };
  const corps = (erreur.error ?? {}) as { detail?: unknown; code?: unknown; erreurs?: { champ: string; message: string }[] };
  const champs: Record<string, string> = {};
  for (const e of corps.erreurs ?? []) champs[e.champ] ??= e.message;
  let message = GENERIQUE;
  if (erreur.status === 0) message = 'Connexion impossible. Vérifiez votre accès à Internet.';
  else if (erreur.status === 429) message = typeof corps.detail === 'string' ? corps.detail : 'Trop de tentatives. Patientez quelques minutes.';
  else if (erreur.status >= 400 && erreur.status < 500 && typeof corps.detail === 'string') message = corps.detail;
  return { message, champs, code: typeof corps.code === 'string' ? corps.code : null, statut: erreur.status };
}

/** Chemin de retour accepté après connexion : un chemin interne uniquement (jamais d'adresse externe). */
export function retourSur(valeur: string | null | undefined): string {
  return valeur && valeur.startsWith('/') && !valeur.startsWith('//') && !valeur.includes('\\') ? valeur : '/compte';
}
