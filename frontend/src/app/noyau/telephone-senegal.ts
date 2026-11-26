/** Préfixes des numéros sénégalais à 9 chiffres (mêmes règles que le backend : TelephoneSenegal). */
const PREFIXES = new Set(['70', '75', '76', '77', '78', '33']);

/**
 * Normalise une saisie au format E.164 (« 77 123 45 67 », « +221 77 123 45 67 », « 00221771234567 »…).
 * @returns le numéro `+221XXXXXXXXX`, ou null si la saisie n'est pas un numéro sénégalais valide
 */
export function normaliserTelephone(saisie: string | null | undefined): string | null {
  if (!saisie) return null;
  let chiffres = saisie.trim().replace(/[\s.\-()/]/g, '');
  if (chiffres.startsWith('+221')) chiffres = chiffres.slice(4);
  else if (chiffres.startsWith('00221')) chiffres = chiffres.slice(5);
  else if (chiffres.startsWith('221') && chiffres.length === 12) chiffres = chiffres.slice(3);
  if (!/^\d{9}$/.test(chiffres) || !PREFIXES.has(chiffres.slice(0, 2))) return null;
  return `+221${chiffres}`;
}

/** « +221771234567 » → « 77 123 45 67 » (affichage). */
export function formaterTelephone(e164: string): string {
  const n = e164.replace('+221', '');
  return `${n.slice(0, 2)} ${n.slice(2, 5)} ${n.slice(5, 7)} ${n.slice(7)}`;
}
