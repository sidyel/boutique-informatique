import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Règles du mot de passe, identiques au serveur (`MotDePasseRobusteValidateur`, ADR 0003 §10). */
export const CARACTERES_MIN = 10;
export const OCTETS_MAX = 72;

export interface EtatMotDePasse {
  longueurOk: boolean;
  octetsOk: boolean;
  valide: boolean;
}

/** Longueur en caractères (un symbole ou une lettre accentuée compte pour un), taille en octets UTF-8. */
export function evaluerMotDePasse(motDePasse: string): EtatMotDePasse {
  const longueurOk = Array.from(motDePasse).length >= CARACTERES_MIN;
  const octetsOk = new TextEncoder().encode(motDePasse).length <= OCTETS_MAX;
  return { longueurOk, octetsOk, valide: longueurOk && octetsOk };
}

/** Validateur de formulaire : même règle que le serveur. */
export function motDePasseValide(controle: AbstractControl<string>): ValidationErrors | null {
  const valeur = controle.value ?? '';
  return valeur === '' || evaluerMotDePasse(valeur).valide ? null : { motDePasse: true };
}

/** Message d'erreur clair pour un mot de passe refusé. */
export function messageMotDePasse(motDePasse: string): string | null {
  if (!motDePasse) return 'Saisissez un mot de passe.';
  const etat = evaluerMotDePasse(motDePasse);
  if (!etat.longueurOk) return `Votre mot de passe est trop court : il faut au moins ${CARACTERES_MIN} caractères.`;
  if (!etat.octetsOk) return 'Votre mot de passe est trop long : raccourcissez-le (72 octets au plus).';
  return null;
}
