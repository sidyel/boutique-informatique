import {
  ElementConfiance,
  FicheProduit,
  GroupeCaracteristiques,
  MediaFiche,
  ProduitResume,
  VarianteFiche,
} from '../../noyau/api/modeles';
import { TITRE_LONGUEUR_MAX } from '../liste-produits/criteres';

/**
 * Logique de la fiche produit, sans Angular : choix de la variante, médias, caractéristiques, textes.
 * Testée dans `fiche.spec.ts`.
 */

// --- Variantes ----------------------------------------------------------------------------------

/** Axe de choix d'une variante : « Mémoire vive » → 8 Go, 16 Go. */
export interface AxeVariante {
  cle: string;
  libelle: string;
  valeurs: string[];
}

/**
 * Option d'un axe, vue depuis la variante courante :
 * - `exacte` : la combinaison (cette valeur + les autres choix actuels) existe ;
 * - sinon l'option est grisée et mène à `variante`, la plus proche qui a cette valeur ;
 * - `rupture` : la variante visée existe mais n'est pas en stock (sélectionnable, bouton panier désactivé).
 */
export interface OptionVariante {
  valeur: string;
  variante: VarianteFiche;
  exacte: boolean;
  courante: boolean;
  rupture: boolean;
}

const LIBELLES_AXES: Record<string, string> = {
  ram: 'Mémoire vive',
  stockage: 'Stockage',
  capacite: 'Capacité',
  couleur: 'Couleur',
  processeur: 'Processeur',
  ecran: 'Écran',
  carte_graphique: 'Carte graphique',
};

export function libelleAxe(cle: string): string {
  const connu = LIBELLES_AXES[cle];
  if (connu) return connu;
  const texte = cle.replace(/[_-]+/g, ' ').trim();
  return texte.charAt(0).toUpperCase() + texte.slice(1);
}

/**
 * Axes qui offrent un vrai choix (au moins deux valeurs), dans l'ordre d'apparition. Les valeurs suivent
 * l'ordre des variantes (de la moins chère à la plus chère).
 */
export function axesVariantes(variantes: readonly VarianteFiche[]): AxeVariante[] {
  const valeursParCle = new Map<string, string[]>();
  for (const variante of variantes) {
    for (const [cle, valeur] of Object.entries(variante.attributs ?? {})) {
      const valeurs = valeursParCle.get(cle) ?? [];
      if (!valeurs.includes(valeur)) valeurs.push(valeur);
      valeursParCle.set(cle, valeurs);
    }
  }
  return [...valeursParCle]
    .filter(([, valeurs]) => valeurs.length > 1)
    .map(([cle, valeurs]) => ({ cle, libelle: libelleAxe(cle), valeurs }));
}

/**
 * Variante affichée à l'ouverture : celle de l'URL (`?variante=`) si elle existe, sinon la moins chère en
 * stock, sinon la moins chère.
 */
export function varianteInitiale(
  variantes: readonly VarianteFiche[],
  idDemande: number | null,
): VarianteFiche | undefined {
  return (
    variantes.find((v) => v.id === idDemande) ??
    variantes.find((v) => v.disponibilite !== 'RUPTURE') ??
    variantes[0]
  );
}

/** Lecture tolérante de `?variante=` : entier positif, sinon `null`. */
export function lireIdVariante(valeur: string | null | undefined): number | null {
  return valeur && /^\d{1,12}$/.test(valeur) ? Number(valeur) : null;
}

/**
 * Variante la plus proche ayant `valeur` pour l'axe `cle` : celle qui garde le plus d'autres choix de la
 * variante courante ; à égalité, une variante en stock, puis le prix le plus proche.
 */
export function plusProche(
  variantes: readonly VarianteFiche[],
  courante: VarianteFiche,
  cle: string,
  valeur: string,
): VarianteFiche | undefined {
  const communs = (v: VarianteFiche) =>
    Object.entries(courante.attributs ?? {}).filter(([c, val]) => c !== cle && v.attributs?.[c] === val).length;
  return variantes
    .filter((v) => v.attributs?.[cle] === valeur)
    .map((v) => ({ v, communs: communs(v), enStock: v.disponibilite !== 'RUPTURE' ? 1 : 0 }))
    .sort(
      (a, b) =>
        b.communs - a.communs ||
        b.enStock - a.enStock ||
        Math.abs(a.v.prix - courante.prix) - Math.abs(b.v.prix - courante.prix),
    )[0]?.v;
}

/** Options de chaque axe, vues depuis la variante courante. */
export function optionsAxe(
  variantes: readonly VarianteFiche[],
  courante: VarianteFiche,
  axe: AxeVariante,
): OptionVariante[] {
  const autres = Object.entries(courante.attributs ?? {}).filter(([c]) => c !== axe.cle);
  return axe.valeurs.flatMap((valeur) => {
    const exacte = variantes.find(
      (v) => v.attributs?.[axe.cle] === valeur && autres.every(([c, val]) => v.attributs?.[c] === val),
    );
    const variante = exacte ?? plusProche(variantes, courante, axe.cle, valeur);
    if (!variante) return [];
    return [
      {
        valeur,
        variante,
        exacte: !!exacte,
        courante: courante.attributs?.[axe.cle] === valeur,
        rupture: variante.disponibilite === 'RUPTURE',
      },
    ];
  });
}

/** « Pas de version 16 Go en Lumière stellaire : voici la plus proche (16 Go, Minuit). » */
export function messagePlusProche(
  axe: AxeVariante,
  option: OptionVariante,
  courante: VarianteFiche,
  axes: readonly AxeVariante[],
): string {
  // « 512 Go n'existe pas avec 8 Go et Minuit » : la valeur demandée, puis les autres choix en cours
  const autres = axes
    .filter((a) => a.cle !== axe.cle)
    .map((a) => courante.attributs?.[a.cle])
    .filter(Boolean);
  const avec = autres.length > 1 ? `${autres.slice(0, -1).join(', ')} et ${autres.at(-1)}` : (autres[0] ?? '');
  return `${option.valeur} n'existe pas avec ${avec} : voici la version la plus proche (${option.variante.libelle}).`;
}

// --- Médias -------------------------------------------------------------------------------------

/**
 * Médias de la galerie pour une variante : ses photos propres d'abord (une couleur), puis les médias
 * communs ; les photos propres aux autres variantes sont écartées.
 */
export function mediasPour(medias: readonly MediaFiche[], varianteId: number | undefined): MediaFiche[] {
  const propres = medias.filter((m) => m.varianteId !== null && m.varianteId === varianteId);
  const communs = medias.filter((m) => m.varianteId === null);
  return [...propres, ...communs];
}

/** URL des photos d'une variante (propres puis communes), pour les données structurées. */
export function imagesVariante(fiche: FicheProduit, varianteId: number): string[] {
  return mediasPour(fiche.medias, varianteId)
    .filter((m) => m.type === 'IMAGE')
    .map((m) => m.url);
}

/** Image d'aperçu d'un média (miniature de la vidéo, ou l'image elle-même). */
export function apercu(media: MediaFiche): string {
  return media.type === 'IMAGE' ? media.url : (media.urlMiniature ?? media.url);
}

// --- Caractéristiques ---------------------------------------------------------------------------

/** Tableau de caractéristiques avec les valeurs propres à la variante choisie. */
export function caracteristiquesPour(
  groupes: readonly GroupeCaracteristiques[],
  variante: VarianteFiche | undefined,
): GroupeCaracteristiques[] {
  const propres = new Map((variante?.caracteristiques ?? []).map((c) => [c.cle, c]));
  return groupes.map((groupe) => ({
    groupe: groupe.groupe,
    elements: groupe.elements.map((element) => {
      const propre = element.parVariante ? propres.get(element.cle) : undefined;
      return propre ? { ...element, valeur: propre.valeur, unite: propre.unite } : element;
    }),
  }));
}

// --- Textes -------------------------------------------------------------------------------------

/** « Asus Vivobook 15 » : la marque devant le nom, sauf si le nom la contient déjà. */
export function nomComplet(fiche: Pick<FicheProduit, 'nom' | 'marque'>): string {
  const marque = fiche.marque.nom.trim();
  return fiche.nom.toLocaleLowerCase('fr').startsWith(marque.toLocaleLowerCase('fr'))
    ? fiche.nom
    : `${marque} ${fiche.nom}`;
}

/**
 * Titre de la page sans le nom du site, pour que « {titre} | {nomSite} » tienne en 60 caractères :
 * metaTitre saisi, sinon « {nom} : prix au Sénégal », sinon le nom seul.
 */
export function titreProduit(nom: string, nomSite: string, metaTitre: string | null | undefined): string {
  const saisi = metaTitre?.trim();
  if (saisi) return saisi;
  const suffixe = ` | ${nomSite}`.length;
  const candidat = `${nom} : prix au Sénégal`;
  return candidat.length + suffixe <= TITRE_LONGUEUR_MAX ? candidat : nom;
}

/** Meta description : celle de l'admin, sinon la description courte, sinon un texte construit. */
export function descriptionProduit(fiche: FicheProduit, nom: string, prix: string): string {
  return (
    fiche.metaDescription?.trim() ||
    fiche.descriptionCourte?.trim() ||
    `${nom} au Sénégal à ${prix} : ${fiche.categorie.nom.toLocaleLowerCase('fr')}, ` +
      `garantie ${fiche.garantieMois} mois, livraison et paiement à la livraison.`
  );
}

/** Message WhatsApp prérempli : produit, variante, référence, prix et lien. */
export function messageWhatsAppProduit(
  nom: string,
  variante: VarianteFiche,
  prix: string,
  url: string,
  plusieursVariantes: boolean,
): string {
  const version = plusieursVariantes ? ` (${variante.libelle})` : '';
  const demande =
    variante.disponibilite === 'RUPTURE'
      ? 'Il est indiqué en rupture de stock : sera-t-il bientôt disponible ?'
      : 'Est-il disponible ? Je souhaite le commander.';
  return `Bonjour, je suis intéressé par ${nom}${version}, réf. ${variante.sku}, à ${prix}. ${demande}\n${url}`;
}

/** Éléments de confiance repris sous le bloc d'achat (la livraison a déjà sa propre ligne). */
export function reassurance(elements: readonly ElementConfiance[]): ElementConfiance[] {
  const retenues = new Set(['GARANTIE', 'PAIEMENT', 'RETOUR', 'SAV']);
  return elements.filter((e) => e.icone !== null && retenues.has(e.icone));
}

/** Résumé de la variante affichée, au format des cartes (récemment consultés, suivi). */
export function resumeProduit(fiche: FicheProduit, variante: VarianteFiche, medias: readonly MediaFiche[]): ProduitResume {
  const image = medias.find((m) => m.type === 'IMAGE');
  return {
    id: fiche.id,
    slug: fiche.slug,
    nom: fiche.nom,
    marque: { nom: fiche.marque.nom, slug: fiche.marque.slug },
    categorie: fiche.categorie,
    image: image ? { url: image.url, texteAlternatif: image.texteAlternatif } : null,
    varianteId: variante.id,
    skuParDefaut: variante.sku,
    prix: variante.prix,
    prixBarre: variante.prixBarre,
    reduction: variante.reduction,
    etat: fiche.etat,
    disponibilite: variante.disponibilite,
    noteMoyenne: fiche.noteMoyenne,
    nombreAvis: fiche.nombreAvis,
    nombreVariantes: fiche.variantes.length,
    caracteristiquesCles: null,
  };
}
