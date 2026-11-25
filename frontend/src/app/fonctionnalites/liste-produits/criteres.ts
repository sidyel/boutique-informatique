import { Params } from '@angular/router';

import { EtatProduit, Filtres } from '../../noyau/api/modeles';
import { formaterPrixFcfa } from '../../partage/pipes/prix-fcfa.pipe';

/**
 * État d'une liste de produits (catégorie, marque, recherche), entièrement décrit par l'URL : une page
 * filtrée se partage, se recharge et revient à l'identique avec le bouton retour.
 *
 * Paramètres : ceux de l'API (`marque`, `etat`, `prixMin`, `prixMax`, `disponible`, `promotion`,
 * `f.cle`, `tri`, `page`, `categorie`). Lecture tolérante : un paramètre inconnu (utm_*, gclid, fbclid…)
 * est ignoré et une valeur mal formée est écartée, pour que le serveur ne renvoie pas d'erreur 400 sur
 * un lien abîmé. Écriture canonique : ordre fixe, valeurs triées, valeurs par défaut omises.
 */

export type CodeTri =
  | 'recommande'
  | 'pertinence'
  | 'prix-croissant'
  | 'prix-decroissant'
  | 'nouveaute'
  | 'popularite'
  | 'note';

export interface OptionTri {
  code: CodeTri;
  libelle: string;
}

/** Tris proposés ; le premier est le tri par défaut, jamais écrit dans l'URL. */
export const TRIS_LISTE: readonly OptionTri[] = [
  { code: 'recommande', libelle: 'Recommandés' },
  { code: 'prix-croissant', libelle: 'Prix croissant' },
  { code: 'prix-decroissant', libelle: 'Prix décroissant' },
  { code: 'nouveaute', libelle: 'Nouveautés' },
  { code: 'popularite', libelle: 'Les plus vendus' },
  { code: 'note', libelle: 'Mieux notés' },
];

export const TRIS_RECHERCHE: readonly OptionTri[] = [
  { code: 'pertinence', libelle: 'Pertinence' },
  ...TRIS_LISTE.slice(1),
];

export interface Criteres {
  categorie: string | null;
  marques: string[];
  etats: EtatProduit[];
  prixMin: number | null;
  prixMax: number | null;
  disponible: boolean;
  promotion: boolean;
  /** Caractéristiques filtrées : `f.ram_go=16` → `{ ram_go: ['16'] }`. */
  caracteristiques: Record<string, string[]>;
  tri: CodeTri | null;
  page: number;
}

export const CRITERES_VIDES: Criteres = {
  categorie: null,
  marques: [],
  etats: [],
  prixMin: null,
  prixMax: null,
  disponible: false,
  promotion: false,
  caracteristiques: {},
  tri: null,
  page: 1,
};

/** Limites du backend (`CriteresProduits`) : au-delà, la valeur est écartée plutôt que de provoquer un 400. */
const PAGE_MAX = 500;
const VALEURS_MAX = 20;
const CARACTERISTIQUES_MAX = 15;
const PREFIXE_CARACTERISTIQUE = 'f.';
const SLUG = /^[a-z0-9][a-z0-9-]{0,219}$/;
const CLE = /^[a-z][a-z0-9_]{0,59}$/;
const MONTANT = /^\d{1,12}$/;
const ETATS: readonly EtatProduit[] = ['NEUF', 'RECONDITIONNE', 'OCCASION'];
const CODES_TRI = new Set<string>([...TRIS_LISTE, ...TRIS_RECHERCHE].map((t) => t.code));

function valeurs(params: Params, cle: string): string[] {
  const brut: unknown = params[cle];
  const liste = Array.isArray(brut) ? brut : brut === undefined || brut === null ? [] : [brut];
  return liste.map((v) => String(v).trim()).filter((v) => v.length > 0);
}

function unique(params: Params, cle: string): string | null {
  return valeurs(params, cle)[0] ?? null;
}

function trie(liste: Iterable<string>): string[] {
  return [...new Set(liste)].sort();
}

function montant(params: Params, cle: string): number | null {
  const valeur = unique(params, cle);
  return valeur !== null && MONTANT.test(valeur) ? Number(valeur) : null;
}

/** Même normalisation que le backend : « 16.0 » → « 16 », pour qu'une case reste cochée. */
export function normaliserValeur(valeur: string): string {
  const nettoyee = valeur.trim();
  return /^-?\d+(\.\d+)?$/.test(nettoyee) ? String(Number(nettoyee)) : nettoyee;
}

/** Lecture tolérante des paramètres d'URL. */
export function lireCriteres(params: Params): Criteres {
  const caracteristiques: Record<string, string[]> = {};
  for (const nom of Object.keys(params).sort()) {
    if (!nom.startsWith(PREFIXE_CARACTERISTIQUE)) {
      continue;
    }
    const cle = nom.slice(PREFIXE_CARACTERISTIQUE.length);
    const liste = trie(valeurs(params, nom).map(normaliserValeur).filter((v) => v.length <= 255));
    if (CLE.test(cle) && liste.length && liste.length <= VALEURS_MAX
        && Object.keys(caracteristiques).length < CARACTERISTIQUES_MAX) {
      caracteristiques[cle] = liste;
    }
  }

  let prixMin = montant(params, 'prixMin');
  let prixMax = montant(params, 'prixMax');
  if (prixMin !== null && prixMax !== null && prixMin > prixMax) {
    [prixMin, prixMax] = [prixMax, prixMin];
  }
  const page = Number(unique(params, 'page'));
  const tri = unique(params, 'tri');
  const categorie = unique(params, 'categorie');

  return {
    categorie: categorie !== null && SLUG.test(categorie) ? categorie : null,
    marques: trie(valeurs(params, 'marque').filter((m) => SLUG.test(m))),
    etats: trie(valeurs(params, 'etat').filter((e) => (ETATS as string[]).includes(e))) as EtatProduit[],
    prixMin,
    prixMax,
    disponible: unique(params, 'disponible') === 'true',
    promotion: unique(params, 'promotion') === 'true',
    caracteristiques,
    tri: tri !== null && CODES_TRI.has(tri) ? (tri as CodeTri) : null,
    page: Number.isInteger(page) && page >= 1 && page <= PAGE_MAX ? page : 1,
  };
}

/**
 * Paramètres canoniques (URL et appels à l'API) : ordre fixe, valeurs triées, défauts omis.
 * @param triParDefaut tri implicite de la page (`recommande`, ou `pertinence` pour la recherche) : omis
 */
export function versParametres(criteres: Criteres, triParDefaut: CodeTri = 'recommande'): Params {
  const params: Params = {};
  if (criteres.categorie) params['categorie'] = criteres.categorie;
  if (criteres.marques.length) params['marque'] = trie(criteres.marques);
  if (criteres.etats.length) params['etat'] = trie(criteres.etats);
  if (criteres.prixMin !== null) params['prixMin'] = String(criteres.prixMin);
  if (criteres.prixMax !== null) params['prixMax'] = String(criteres.prixMax);
  if (criteres.disponible) params['disponible'] = 'true';
  if (criteres.promotion) params['promotion'] = 'true';
  for (const cle of Object.keys(criteres.caracteristiques).sort()) {
    const liste = criteres.caracteristiques[cle];
    if (liste.length) params[PREFIXE_CARACTERISTIQUE + cle] = trie(liste);
  }
  if (criteres.tri && criteres.tri !== triParDefaut) params['tri'] = criteres.tri;
  if (criteres.page > 1) params['page'] = String(criteres.page);
  return params;
}

/** Changement de filtre : on revient toujours à la première page (la page 3 n'existe peut-être plus). */
export function avecFiltre(criteres: Criteres, changement: Partial<Omit<Criteres, 'page' | 'tri'>>): Criteres {
  return { ...criteres, ...changement, page: 1 };
}

export function basculer<T extends string>(liste: readonly T[], valeur: T): T[] {
  return liste.includes(valeur) ? liste.filter((v) => v !== valeur) : [...liste, valeur];
}

export function basculerCaracteristique(criteres: Criteres, cle: string, valeur: string): Criteres {
  const caracteristiques = { ...criteres.caracteristiques, [cle]: basculer(criteres.caracteristiques[cle] ?? [], valeur) };
  if (!caracteristiques[cle].length) delete caracteristiques[cle];
  return avecFiltre(criteres, { caracteristiques });
}

/** Tous les filtres retirés ; le tri choisi est conservé. */
export function sansFiltres(criteres: Criteres): Criteres {
  return { ...CRITERES_VIDES, tri: criteres.tri };
}

/** Nombre de filtres actifs (pastille du bouton « Filtrer »). */
export function nombreFiltres(criteres: Criteres): number {
  return (
    (criteres.categorie ? 1 : 0) +
    criteres.marques.length +
    criteres.etats.length +
    (criteres.prixMin !== null || criteres.prixMax !== null ? 1 : 0) +
    (criteres.disponible ? 1 : 0) +
    (criteres.promotion ? 1 : 0) +
    Object.values(criteres.caracteristiques).reduce((total, liste) => total + liste.length, 0)
  );
}

/** Filtre actif affiché en puce ; un toucher le retire. */
export interface PuceFiltre {
  id: string;
  libelle: string;
  sans: Criteres;
}

/** Puces des filtres actifs, avec les libellés des facettes quand elles sont connues. */
export function pucesActives(criteres: Criteres, filtres: Filtres | undefined): PuceFiltre[] {
  const libelle = (options: { valeur: string; libelle: string }[] | undefined, valeur: string) =>
    options?.find((o) => o.valeur === valeur)?.libelle ?? valeur;
  const puces: PuceFiltre[] = [];
  if (criteres.categorie) {
    puces.push({
      id: 'categorie',
      libelle: libelle(filtres?.sousCategories, criteres.categorie),
      sans: avecFiltre(criteres, { categorie: null }),
    });
  }
  for (const marque of criteres.marques) {
    puces.push({
      id: `marque-${marque}`,
      libelle: libelle(filtres?.marques, marque),
      sans: avecFiltre(criteres, { marques: basculer(criteres.marques, marque) }),
    });
  }
  for (const etat of criteres.etats) {
    puces.push({
      id: `etat-${etat}`,
      libelle: libelle(filtres?.etats, etat),
      sans: avecFiltre(criteres, { etats: basculer(criteres.etats, etat) }),
    });
  }
  if (criteres.prixMin !== null || criteres.prixMax !== null) {
    puces.push({
      id: 'prix',
      libelle: libellePrix(criteres.prixMin, criteres.prixMax),
      sans: avecFiltre(criteres, { prixMin: null, prixMax: null }),
    });
  }
  if (criteres.disponible) {
    puces.push({ id: 'disponible', libelle: 'En stock', sans: avecFiltre(criteres, { disponible: false }) });
  }
  if (criteres.promotion) {
    puces.push({ id: 'promotion', libelle: 'En promotion', sans: avecFiltre(criteres, { promotion: false }) });
  }
  for (const [cle, liste] of Object.entries(criteres.caracteristiques)) {
    const definition = filtres?.caracteristiques.find((c) => c.cle === cle);
    for (const valeur of liste) {
      puces.push({
        id: `f-${cle}-${valeur}`,
        libelle: definition ? `${definition.libelle} : ${libelle(definition.valeurs, valeur)}` : valeur,
        sans: basculerCaracteristique(criteres, cle, valeur),
      });
    }
  }
  return puces;
}

// --- Prix ---------------------------------------------------------------------------------------

export interface TranchePrix {
  libelle: string;
  prixMin: number | null;
  prixMax: number | null;
}

/**
 * « 200 000 – 400 000 FCFA », « Jusqu'à 200 000 FCFA », « 400 000 FCFA et plus ».
 * @param devise `false` pour les tranches, sous un titre « Prix (FCFA) » : deux tranches par ligne à 360 px
 */
export function libellePrix(prixMin: number | null, prixMax: number | null, devise = true): string {
  const montant = (m: number | null) => formaterPrixFcfa(m).replace(/\s*FCFA$/, '');
  const suffixe = devise ? '\u00a0FCFA' : '';
  if (prixMin !== null && prixMax !== null) {
    return `${montant(prixMin)} – ${montant(prixMax)}${suffixe}`;
  }
  return prixMin !== null ? `${montant(prixMin)}${suffixe} et plus` : `Jusqu'à ${montant(prixMax)}${suffixe}`;
}

/** Pas « lisibles » : 1, 2, 2,5 et 5 × 10^n. */
function pasLisible(brut: number): number {
  const puissance = 10 ** Math.floor(Math.log10(brut));
  for (const facteur of [1, 2, 2.5, 5, 10]) {
    if (facteur * puissance >= brut) return facteur * puissance;
  }
  return 10 * puissance;
}

const PAS_MINIMAL = 5_000;

/**
 * 3 à 5 tranches de prix cliquables, bornées par des montants ronds, à partir de la plage des facettes.
 * Bornes incluses des deux côtés (`v.prix >= prixMin` et `v.prix <= prixMax` côté API) : « Jusqu'à
 * 200 000 », « 200 000 – 400 000 », « 400 000 et plus » (devise dans le titre). Plage trop étroite : aucune.
 */
export function tranchesPrix(min: number, max: number): TranchePrix[] {
  if (!(max > min)) return [];
  const pas = Math.max(PAS_MINIMAL, pasLisible((max - min) / 4));
  const bornes: number[] = [];
  for (let borne = Math.floor(min / pas) * pas + pas; borne < max && bornes.length < 4; borne += pas) {
    bornes.push(borne);
  }
  if (bornes.length < 2) return [];
  const tranches: TranchePrix[] = [{ libelle: libellePrix(null, bornes[0], false), prixMin: null, prixMax: bornes[0] }];
  for (let i = 0; i < bornes.length - 1; i++) {
    tranches.push({ libelle: libellePrix(bornes[i], bornes[i + 1], false), prixMin: bornes[i], prixMax: bornes[i + 1] });
  }
  const derniere = bornes[bornes.length - 1];
  tranches.push({ libelle: libellePrix(derniere, null, false), prixMin: derniere, prixMax: null });
  return tranches;
}

// --- Titres -------------------------------------------------------------------------------------

/** Longueur recommandée d'un titre, nom du site compris (au-delà, Google le coupe). */
export const TITRE_LONGUEUR_MAX = 60;

/**
 * Titre d'une page catégorie ou marque (sans le nom du site, ajouté par le SeoService) :
 * le meta-titre de l'administrateur s'il existe, sinon la forme la plus riche qui tient en 60 caractères
 * avec « | {nom du site} » : « PC portables au Sénégal : prix en FCFA », puis « … au Sénégal », puis le nom.
 */
export function titreListe(nom: string, nomSite: string, metaTitre: string | null | undefined): string {
  if (metaTitre?.trim()) return metaTitre.trim();
  const suffixe = ` | ${nomSite}`.length;
  const formes = [`${nom} au Sénégal : prix en FCFA`, `${nom} au Sénégal`];
  return formes.find((titre) => titre.length + suffixe <= TITRE_LONGUEUR_MAX) ?? nom;
}
