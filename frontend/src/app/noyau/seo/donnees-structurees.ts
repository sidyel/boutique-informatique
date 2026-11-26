import {
  Avis,
  Disponibilite,
  EtatProduit,
  FicheProduit,
  JourSemaine,
  Page,
  ParametresPublics,
  ProduitResume,
  VarianteFiche,
} from '../api/modeles';

/**
 * Données structurées schema.org (JSON-LD), en fonctions pures : chaque modèle est testé seul.
 * Règles (ADR 0007) :
 * - URL et images absolues (`urlSite` = URL_SITE, sans barre finale) ;
 * - montants en FCFA entiers, `priceCurrency: "XOF"` ;
 * - les identifiants produits (`sku`) sont ceux des événements de suivi et des flux (SKU de la variante) ;
 * - `aggregateRating` et `review` seulement si des avis sont affichés sur la page ;
 * - pas de `shippingDetails` : Google ne gère les régions que pour les États-Unis, l'Australie et le Japon,
 *   le tarif annoncé à Google sera décidé en phase 7 (Merchant Center).
 */
export type ObjetLd = Record<string, unknown>;

const SCHEMA = 'https://schema.org';
const PAYS = 'SN';
const DEVISE = 'XOF';

/** Identifiants stables, référencés d'un bloc à l'autre (`{"@id": …}`). */
export const idOrganisation = (urlSite: string) => `${urlSite}/#organisation`;
export const idPolitiqueRetour = (urlSite: string) => `${urlSite}/#politique-retour`;
export const idBoutique = (urlSite: string) => `${urlSite}/#boutique`;

/** Chemin relatif → URL absolue ; une URL déjà absolue est gardée. */
export function absolue(urlSite: string, chemin: string): string {
  if (/^https?:\/\//i.test(chemin)) return chemin;
  return `${urlSite}${chemin.startsWith('/') ? '' : '/'}${chemin}`;
}

/** Sérialisation sûre dans `<script type="application/ld+json">` : aucun texte ne peut fermer la balise. */
export function serialiserLd(objet: ObjetLd): string {
  return JSON.stringify(objet)
    .replace(/</g, '\\u003c')
    .replace(/>/g, '\\u003e')
    .replace(/&/g, '\\u0026')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
}

const DISPONIBILITES: Record<Disponibilite, string> = {
  EN_STOCK: `${SCHEMA}/InStock`,
  STOCK_FAIBLE: `${SCHEMA}/LimitedAvailability`,
  RUPTURE: `${SCHEMA}/OutOfStock`,
};

const ETATS: Record<EtatProduit, string> = {
  NEUF: `${SCHEMA}/NewCondition`,
  RECONDITIONNE: `${SCHEMA}/RefurbishedCondition`,
  OCCASION: `${SCHEMA}/UsedCondition`,
};

const JOURS: Record<JourSemaine, string> = {
  MONDAY: `${SCHEMA}/Monday`,
  TUESDAY: `${SCHEMA}/Tuesday`,
  WEDNESDAY: `${SCHEMA}/Wednesday`,
  THURSDAY: `${SCHEMA}/Thursday`,
  FRIDAY: `${SCHEMA}/Friday`,
  SATURDAY: `${SCHEMA}/Saturday`,
  SUNDAY: `${SCHEMA}/Sunday`,
};

/** Retire les propriétés vides (null, undefined, '', tableau vide) : le JSON-LD reste minimal. */
function compact(objet: ObjetLd): ObjetLd {
  return Object.fromEntries(
    Object.entries(objet).filter(
      ([, v]) => v !== null && v !== undefined && v !== '' && !(Array.isArray(v) && v.length === 0),
    ),
  );
}

// --- Organisation, politique de retour, boutique ---------------------------------------------------

/** Politique de retour (MerchantReturnPolicy), partagée par l'organisation et les offres. */
export function politiqueRetour(parametres: ParametresPublics, urlSite: string): ObjetLd | null {
  const p = parametres.politiqueRetour;
  if (!p) return null;
  if (!p.acceptes || !p.delaiJours) {
    return {
      '@type': 'MerchantReturnPolicy',
      '@id': idPolitiqueRetour(urlSite),
      applicableCountry: PAYS,
      returnPolicyCategory: `${SCHEMA}/MerchantReturnNotPermitted`,
    };
  }
  const modes = [
    ...(p.enBoutique ? [`${SCHEMA}/ReturnInStore`] : []),
    // Reprise par un livreur : le plus proche de « par courrier » dans le vocabulaire schema.org
    ...(p.parLivreur ? [`${SCHEMA}/ReturnByMail`] : []),
  ];
  return compact({
    '@type': 'MerchantReturnPolicy',
    '@id': idPolitiqueRetour(urlSite),
    applicableCountry: PAYS,
    returnPolicyCategory: `${SCHEMA}/MerchantReturnFiniteReturnWindow`,
    merchantReturnDays: p.delaiJours,
    returnMethod: modes.length === 1 ? modes[0] : modes,
    returnFees:
      p.frais === 'GRATUIT' ? `${SCHEMA}/FreeReturn` : `${SCHEMA}/ReturnFeesCustomerResponsibility`,
  });
}

function adressePostale(parametres: ParametresPublics): ObjetLd | null {
  const a = parametres.adresseStructuree;
  if (!a) return null;
  return compact({
    '@type': 'PostalAddress',
    streetAddress: a.rue,
    addressLocality: a.ville,
    addressRegion: a.region,
    postalCode: a.codePostal,
    addressCountry: PAYS,
  });
}

/** Réseaux sociaux : uniquement des URL https (une saisie incomplète est ignorée). */
function reseauxSociaux(parametres: ParametresPublics): string[] {
  return Object.values(parametres.liensReseauxSociaux ?? {}).filter((u) => /^https:\/\/[^/]+\/.+/.test(u ?? ''));
}

/** Organization, sur toutes les pages : identité, contact, adresse et politique de retour. */
export function organisation(parametres: ParametresPublics, nomSite: string, urlSite: string): ObjetLd {
  return compact({
    '@type': 'Organization',
    '@id': idOrganisation(urlSite),
    name: nomSite,
    url: `${urlSite}/`,
    logo: parametres.logoUrl ? absolue(urlSite, parametres.logoUrl) : null,
    image: parametres.imagePartageUrl ? absolue(urlSite, parametres.imagePartageUrl) : null,
    telephone: parametres.telephone,
    email: parametres.email,
    address: adressePostale(parametres),
    sameAs: reseauxSociaux(parametres),
    hasMerchantReturnPolicy: politiqueRetour(parametres, urlSite),
  });
}

/** Store (LocalBusiness) : seulement si une boutique physique avec adresse complète est déclarée. */
export function boutique(parametres: ParametresPublics, nomSite: string, urlSite: string): ObjetLd | null {
  const b = parametres.boutiquePhysique;
  const adresse = adressePostale(parametres);
  if (!b || !adresse) return null;
  return compact({
    '@type': 'Store',
    '@id': idBoutique(urlSite),
    name: nomSite,
    url: `${urlSite}/`,
    image: parametres.logoUrl ? absolue(urlSite, parametres.logoUrl) : null,
    telephone: parametres.telephone,
    address: adresse,
    geo:
      b.latitude !== null && b.longitude !== null
        ? { '@type': 'GeoCoordinates', latitude: b.latitude, longitude: b.longitude }
        : null,
    openingHoursSpecification: b.horaires.map((h) => ({
      '@type': 'OpeningHoursSpecification',
      dayOfWeek: h.jours.map((j) => JOURS[j]),
      opens: h.ouverture,
      closes: h.fermeture,
    })),
    currenciesAccepted: DEVISE,
    parentOrganization: { '@id': idOrganisation(urlSite) },
  });
}

/** WebSite : nom du site affiché par Google dans les résultats. */
export function siteWeb(nomSite: string, urlSite: string): ObjetLd {
  return {
    '@type': 'WebSite',
    name: nomSite,
    url: `${urlSite}/`,
    inLanguage: 'fr-SN',
    publisher: { '@id': idOrganisation(urlSite) },
  };
}

// --- Navigation et listes --------------------------------------------------------------------------

/** Fil d'Ariane : le dernier élément (la page elle-même) n'a pas de lien. */
export function filAriane(etapes: { nom: string; lien?: string }[], urlSite: string): ObjetLd {
  return {
    '@type': 'BreadcrumbList',
    itemListElement: [{ nom: 'Accueil', lien: '/' }, ...etapes].map((e, i) =>
      compact({
        '@type': 'ListItem',
        position: i + 1,
        name: e.nom,
        item: e.lien ? absolue(urlSite, e.lien) : null,
      }),
    ),
  };
}

/** Produits affichés par une liste (catégorie, marque), dans l'ordre de la page. */
export function listeProduits(produits: ProduitResume[], urlSite: string, decalage = 0): ObjetLd {
  return {
    '@type': 'ItemList',
    itemListElement: produits.map((p, i) => ({
      '@type': 'ListItem',
      position: decalage + i + 1,
      url: absolue(urlSite, `/produit/${p.slug}`),
      name: p.nom,
    })),
  };
}

// --- Produit ---------------------------------------------------------------------------------------

/** Date « 2026-11-24 » (UTC) pour `datePublished`. */
function jour(iso: string): string | null {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? null : date.toISOString().slice(0, 10);
}

function avisLd(avis: Avis): ObjetLd {
  return compact({
    '@type': 'Review',
    author: { '@type': 'Person', name: avis.auteur },
    datePublished: jour(avis.date),
    name: avis.titre,
    reviewBody: avis.commentaire,
    reviewRating: { '@type': 'Rating', ratingValue: avis.note, bestRating: 5, worstRating: 1 },
  });
}

/** Note moyenne et avis : uniquement ceux affichés sur la page (règle de Google). */
function notes(fiche: FicheProduit, avis: Page<Avis> | undefined): ObjetLd {
  if (!avis?.elements.length || fiche.nombreAvis < 1 || fiche.noteMoyenne === null) return {};
  return {
    aggregateRating: {
      '@type': 'AggregateRating',
      ratingValue: Math.round(fiche.noteMoyenne * 10) / 10,
      reviewCount: fiche.nombreAvis,
      bestRating: 5,
      worstRating: 1,
    },
    review: avis.elements.map(avisLd),
  };
}

function offre(fiche: FicheProduit, variante: VarianteFiche, urlSite: string, plusieurs: boolean): ObjetLd {
  const chemin = `/produit/${fiche.slug}` + (plusieurs ? `?variante=${variante.id}` : '');
  const barre = variante.prixBarre && variante.prixBarre > variante.prix ? variante.prixBarre : null;
  return compact({
    '@type': 'Offer',
    url: absolue(urlSite, chemin),
    price: variante.prix,
    priceCurrency: DEVISE,
    // Prix barré (promotion) : affiché par Google à côté du prix
    priceSpecification: barre
      ? {
          '@type': 'UnitPriceSpecification',
          priceType: `${SCHEMA}/StrikethroughPrice`,
          price: barre,
          priceCurrency: DEVISE,
        }
      : null,
    availability: DISPONIBILITES[variante.disponibilite],
    itemCondition: ETATS[fiche.etat],
    seller: { '@id': idOrganisation(urlSite) },
    hasMerchantReturnPolicy: { '@id': idPolitiqueRetour(urlSite) },
  });
}

/** Clé d'attribut de la couleur (seul axe de nos variantes que Google reconnaît dans `variesBy`). */
const CLE_COULEUR = 'couleur';

/**
 * Fiche produit :
 * - une variante : `Product` avec son `Offer` ;
 * - plusieurs : `ProductGroup` (une page, une canonique) avec un `Product` par variante, chacun avec son
 *   offre, son SKU et son URL `?variante=`.
 *
 * @param images URL des images par variante (photos propres d'abord, puis communes)
 */
export function produit(
  fiche: FicheProduit,
  nomComplet: string,
  images: (variante: VarianteFiche) => string[],
  avis: Page<Avis> | undefined,
  urlSite: string,
): ObjetLd {
  const marque = { '@type': 'Brand', name: fiche.marque.nom };
  const description = fiche.descriptionCourte?.trim() || null;
  const plusieurs = fiche.variantes.length > 1;
  const imagesAbsolues = (v: VarianteFiche) => images(v).map((u) => absolue(urlSite, u));

  if (!plusieurs) {
    const v = fiche.variantes[0];
    return compact({
      '@type': 'Product',
      name: nomComplet,
      description,
      url: absolue(urlSite, `/produit/${fiche.slug}`),
      image: imagesAbsolues(v),
      sku: v.sku,
      gtin: fiche.gtin,
      mpn: fiche.mpn,
      brand: marque,
      category: fiche.categorie.nom,
      offers: offre(fiche, v, urlSite, false),
      ...notes(fiche, avis),
    });
  }

  const couleurVarie = new Set(fiche.variantes.map((v) => v.attributs?.[CLE_COULEUR]).filter(Boolean)).size > 1;
  return compact({
    '@type': 'ProductGroup',
    name: nomComplet,
    description,
    url: absolue(urlSite, `/produit/${fiche.slug}`),
    // Même valeur que item_group_id des flux produits (phase 7)
    productGroupID: String(fiche.id),
    brand: marque,
    category: fiche.categorie.nom,
    // Google ne reconnaît que color, size, material, pattern… : mémoire et stockage n'y figurent pas
    variesBy: couleurVarie ? [`${SCHEMA}/color`] : null,
    hasVariant: fiche.variantes.map((v) =>
      compact({
        '@type': 'Product',
        name: `${nomComplet} – ${v.libelle}`,
        sku: v.sku,
        image: imagesAbsolues(v),
        color: couleurVarie ? v.attributs?.[CLE_COULEUR] : null,
        offers: offre(fiche, v, urlSite, true),
      }),
    ),
    ...notes(fiche, avis),
  });
}

/** Regroupe les blocs d'une page dans un seul `@graph` (références `@id` résolues entre eux). */
export function graphe(blocs: (ObjetLd | null)[]): ObjetLd {
  return { '@context': SCHEMA, '@graph': blocs.filter((b): b is ObjetLd => b !== null) };
}
