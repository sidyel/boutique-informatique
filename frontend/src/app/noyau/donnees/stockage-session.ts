/**
 * Accès à sessionStorage qui ne plante jamais : navigation privée de certains navigateurs mobiles, stockage plein
 * ou désactivé lèvent des exceptions. Dans ce cas, la fonctionnalité continue sans mémoire (le formulaire n'est
 * simplement pas restauré). À n'appeler que dans le navigateur (jamais pendant le rendu serveur).
 */
export function lireSession(cle: string): string | null {
  try {
    return sessionStorage.getItem(cle);
  } catch {
    return null;
  }
}

export function ecrireSession(cle: string, valeur: string): void {
  try {
    sessionStorage.setItem(cle, valeur);
  } catch {
    // Stockage indisponible : tant pis, rien n'est conservé
  }
}

export function effacerSession(cle: string): void {
  try {
    sessionStorage.removeItem(cle);
  } catch {
    // Rien à effacer
  }
}
