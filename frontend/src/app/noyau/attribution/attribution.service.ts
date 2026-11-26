import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

import { AttributionCommande } from '../api/modeles';
import { PlateformeService } from '../plateforme.service';

const NOM_COOKIE = 'bi_attribution';
const DUREE_JOURS = 90;
/** Longueurs réduites : le cookie garde deux contacts et ne doit pas dépasser 4 Ko. */
const LONGUEUR_MAX = 100;

/** Identifiants individuels de régies publicitaires : jamais gardés ici (consentement, phase 7). */
const PARAMETRES_IDENTIFIANTS = new Set(['gclid', 'fbclid', 'msclkid', 'wbraid', 'gbraid', 'dclid', 'ttclid']);
const MOTEURS_DE_RECHERCHE = /(^|\.)(google|bing|duckduckgo|yahoo|ecosia|qwant|yandex|baidu)\./;

/** Un contact avec le site : d'où vient le visiteur, sur quelle page il est arrivé. */
export interface Touche {
  source: string;
  medium: string;
  campagne: string | null;
  contenu: string | null;
  terme: string | null;
  page: string;
  referent: string | null;
  /** Visite sans UTM ni site référent : ne remplace jamais un contact connu. */
  direct: boolean;
}

interface Attribution {
  premier: Touche;
  dernier: Touche;
}

/**
 * Origine marketing du visiteur (UTM, page d'arrivée, référent), gardée 90 jours dans un cookie premier-partie
 * (`bi_attribution`) et envoyée avec la commande, pour savoir quelle publicité a vendu.
 *
 * - **Premier contact** : le premier contact connu, gardé tel quel. **Dernier contact** : le dernier contact non direct.
 * - **Aucun identifiant individuel** (gclid, fbclid…) : ils sont retirés de l'URL gardée. Leur capture dépendra du
 *   consentement (phase 7, `ConsentementService` côté serveur).
 * - Aucun accès au navigateur pendant le rendu serveur.
 */
@Injectable({ providedIn: 'root' })
export class AttributionService {
  private readonly document = inject(DOCUMENT);
  private readonly navigateur = inject(PlateformeService).estNavigateur;

  /** À appeler une fois au démarrage dans le navigateur : enregistre le contact de cette visite. */
  capturer(): void {
    if (!this.navigateur) return;
    try {
      const location = this.document.location;
      const touche = construireTouche(location.href, this.document.referrer, location.hostname);
      const existante = lireCookie(this.document);
      if (touche.direct && existante) return;
      const premier = existante?.premier ?? touche;
      const dernier = touche.direct ? (existante?.dernier ?? touche) : touche;
      ecrireCookie(this.document, { premier, dernier }, location.protocol === 'https:');
    } catch {
      // Cookies bloqués ou valeur illisible : pas d'attribution, la commande n'en dépend pas
    }
  }

  /** Attribution à joindre à la commande ; null si le visiteur n'a rien enregistré. */
  lire(): AttributionCommande | null {
    if (!this.navigateur) return null;
    const attribution = lireCookie(this.document);
    if (!attribution) return null;
    const { premier, dernier } = attribution;
    return {
      utmSource: dernier.source,
      utmMedium: dernier.medium,
      utmCampagne: dernier.campagne,
      utmContenu: dernier.contenu,
      utmTerme: dernier.terme,
      pageArrivee: dernier.page,
      referent: dernier.referent,
      premiereSource: premier.source,
      premierMedium: premier.medium,
      premiereCampagne: premier.campagne,
      premierePageArrivee: premier.page,
    };
  }
}

/** Contact correspondant à une arrivée sur `href` depuis `referrer` (fonction pure, testée). */
export function construireTouche(href: string, referrer: string, hoteDuSite: string): Touche {
  const url = new URL(href);
  const utm = (nom: string) => nettoyer(url.searchParams.get(nom));
  const source = utm('utm_source');
  const page = pageSansIdentifiants(url);
  const referent = referrer ? nettoyer(referrer, 200) : null;
  if (source) {
    return {
      source,
      medium: utm('utm_medium') ?? '(none)',
      campagne: utm('utm_campaign'),
      contenu: utm('utm_content'),
      terme: utm('utm_term'),
      page,
      referent,
      direct: false,
    };
  }
  const hoteReferent = hoteDe(referrer);
  if (hoteReferent && hoteReferent !== hoteDuSite.toLowerCase()) {
    return {
      source: hoteReferent,
      medium: MOTEURS_DE_RECHERCHE.test(hoteReferent) ? 'organic' : 'referral',
      campagne: null,
      contenu: null,
      terme: null,
      page,
      referent,
      direct: false,
    };
  }
  return { source: '(direct)', medium: '(none)', campagne: null, contenu: null, terme: null, page, referent: null, direct: true };
}

/** Chemin et paramètres de la page d'arrivée, sans identifiant individuel de régie. */
function pageSansIdentifiants(url: URL): string {
  const parametres = new URLSearchParams();
  url.searchParams.forEach((valeur, nom) => {
    if (!PARAMETRES_IDENTIFIANTS.has(nom.toLowerCase())) parametres.append(nom, valeur);
  });
  const requete = parametres.toString();
  return (url.pathname + (requete ? `?${requete}` : '')).slice(0, 300);
}

function hoteDe(adresse: string): string | null {
  try {
    return adresse ? new URL(adresse).hostname.toLowerCase() : null;
  } catch {
    return null;
  }
}

function nettoyer(valeur: string | null, longueurMax = LONGUEUR_MAX): string | null {
  if (!valeur) return null;
  // Sans caractères de contrôle, tronqué
  const propre = valeur.replace(/[\u0000-\u001f\u007f]/g, ' ').trim();
  return propre ? propre.slice(0, longueurMax) : null;
}

function lireCookie(document: Document): Attribution | null {
  const paire = document.cookie.split('; ').find((c) => c.startsWith(`${NOM_COOKIE}=`));
  if (!paire) return null;
  try {
    const valeur = JSON.parse(decodeURIComponent(paire.slice(NOM_COOKIE.length + 1))) as Partial<Attribution>;
    return valeur?.premier && valeur?.dernier ? (valeur as Attribution) : null;
  } catch {
    return null;
  }
}

function ecrireCookie(document: Document, attribution: Attribution, securise: boolean): void {
  const valeur = encodeURIComponent(JSON.stringify(attribution));
  document.cookie =
    `${NOM_COOKIE}=${valeur}; Max-Age=${DUREE_JOURS * 86_400}; Path=/; SameSite=Lax${securise ? '; Secure' : ''}`;
}
