import { ModeLivraison, ModePaiement } from '../../noyau/api/modeles';
import { normaliserTelephone } from '../../noyau/telephone-senegal';

/** Saisies du tunnel, conservées dans sessionStorage tant que la commande n'est pas passée. */
export interface BrouillonCommande {
  nom: string;
  telephone: string;
  email: string;
  modeLivraison: ModeLivraison;
  zoneId: number | null;
  ville: string;
  quartier: string;
  adresse: string;
  pointDeRepere: string;
  telephoneLivraison: string;
  modePaiement: ModePaiement | null;
}

export const CLE_BROUILLON = 'commande_brouillon';

export function brouillonVide(): BrouillonCommande {
  return {
    nom: '',
    telephone: '',
    email: '',
    modeLivraison: 'DOMICILE',
    zoneId: null,
    ville: 'Dakar',
    quartier: '',
    adresse: '',
    pointDeRepere: '',
    telephoneLivraison: '',
    modePaiement: null,
  };
}

const MODES_LIVRAISON = new Set<string>(['DOMICILE', 'RETRAIT_MAGASIN']);
const MODES_PAIEMENT = new Set<string>(['MOBILE_MONEY', 'CARTE_BANCAIRE', 'A_LA_LIVRAISON']);

/**
 * Relit un brouillon enregistré. Le stockage peut contenir n'importe quoi (ancienne version, valeur modifiée à la
 * main) : chaque champ est vérifié, sinon la valeur par défaut est gardée.
 */
export function lireBrouillon(brut: string | null): BrouillonCommande {
  const base = brouillonVide();
  if (!brut) return base;
  try {
    const v = JSON.parse(brut) as Record<string, unknown>;
    if (v === null || typeof v !== 'object') return base;
    const texte = (cle: keyof BrouillonCommande, max: number): string =>
      typeof v[cle] === 'string' ? (v[cle] as string).slice(0, max) : (base[cle] as string);
    return {
      nom: texte('nom', 120),
      telephone: texte('telephone', 30),
      email: texte('email', 254),
      modeLivraison: MODES_LIVRAISON.has(String(v['modeLivraison'])) ? (v['modeLivraison'] as ModeLivraison) : base.modeLivraison,
      zoneId: Number.isInteger(v['zoneId']) && (v['zoneId'] as number) > 0 ? (v['zoneId'] as number) : null,
      ville: texte('ville', 80),
      quartier: texte('quartier', 80),
      adresse: texte('adresse', 255),
      pointDeRepere: texte('pointDeRepere', 255),
      telephoneLivraison: texte('telephoneLivraison', 30),
      modePaiement: MODES_PAIEMENT.has(String(v['modePaiement'])) ? (v['modePaiement'] as ModePaiement) : null,
    };
  } catch {
    return base;
  }
}

export type Erreurs = Partial<Record<'nom' | 'telephone' | 'email' | 'zone' | 'ville' | 'quartier' | 'adresse' | 'telephoneLivraison' | 'paiement', string>>;

const FORMAT_EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** Étape 1 : nom et téléphone obligatoires, e-mail facultatif mais valide s'il est donné. */
export function erreursCoordonnees(b: BrouillonCommande): Erreurs {
  const e: Erreurs = {};
  if (!b.nom.trim()) e.nom = 'Indiquez votre nom.';
  if (!normaliserTelephone(b.telephone)) {
    e.telephone = 'Indiquez un numéro sénégalais valide, par exemple 77 123 45 67.';
  }
  if (b.email.trim() && !FORMAT_EMAIL.test(b.email.trim())) e.email = 'Cette adresse e-mail n’est pas valide.';
  return e;
}

/** Étape 2 : à domicile, une zone, une ville, un quartier, et l'adresse ou un point de repère. */
export function erreursLivraison(b: BrouillonCommande): Erreurs {
  const e: Erreurs = {};
  if (b.modeLivraison === 'RETRAIT_MAGASIN') return e;
  if (b.zoneId === null) e.zone = 'Choisissez votre zone de livraison.';
  if (!b.ville.trim()) e.ville = 'Indiquez la ville.';
  if (!b.quartier.trim()) e.quartier = 'Indiquez le quartier.';
  if (!b.adresse.trim() && !b.pointDeRepere.trim()) {
    e.adresse = 'Indiquez l’adresse, ou un point de repère connu (mosquée, boutique, école…).';
  }
  if (b.telephoneLivraison.trim() && !normaliserTelephone(b.telephoneLivraison)) {
    e.telephoneLivraison = 'Numéro invalide : laissez vide pour utiliser le vôtre.';
  }
  return e;
}

/** Étape 3 : un mode de paiement, disponible pour ce montant. */
export function erreursPaiement(b: BrouillonCommande, disponibles: readonly ModePaiement[]): Erreurs {
  if (!b.modePaiement || !disponibles.includes(b.modePaiement)) {
    return { paiement: 'Choisissez un mode de paiement.' };
  }
  return {};
}

export function aDesErreurs(erreurs: Erreurs): boolean {
  return Object.keys(erreurs).length > 0;
}
