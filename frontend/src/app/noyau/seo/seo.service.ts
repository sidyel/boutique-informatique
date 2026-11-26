import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

import { ParametresSiteService } from '../donnees/parametres-site.service';
import { ObjetLd, boutique, graphe, organisation, serialiserLd } from './donnees-structurees';

const ID_SCRIPT_LD = 'donnees-structurees';

export interface InformationsPage {
  /** Titre sans le nom du site : « PC portables au meilleur prix au Sénégal ». Vide : nom du site seul. */
  titre: string;
  description?: string;
  /** Pages privées, de test, d'erreur, de recherche ou filtrées : `noindex`. */
  indexable?: boolean;
  /**
   * Chemin canonique, relatif au site : « /produit/x », « /categorie/y?page=2 ». Rendu en URL absolue
   * (`URL_SITE`). Absent : aucune balise canonique (pages d'erreur).
   */
  canonique?: string;
  /** Image de partage propre à la page ; absente : image de partage par défaut du site. */
  image?: string | null;
  /** `og:type` : `product` sur la fiche produit. */
  type?: 'website' | 'product';
  /** Prix affiché (`product:price:amount`, en FCFA) : fiche produit uniquement. */
  prix?: number | null;
  /**
   * Blocs JSON-LD propres à la page (produit, fil d'Ariane, liste…), construits avec l'URL du site
   * (`SeoService.urlSite()`). L'organisation (et la boutique physique) sont ajoutées sur toutes les pages.
   */
  donneesStructurees?: (urlSite: string) => (ObjetLd | null)[];
}

/**
 * Service SEO central. Fonctionne pendant le rendu serveur : les balises sont dans le HTML envoyé.
 * - Page : titre « {titre} | {nom du site} », meta description, robots, canonique absolue, Open Graph,
 *   Twitter Card.
 * - Indexation : `noindex` sur toutes les pages tant que le serveur ne l'autorise pas
 *   (`INDEXATION_AUTORISEE`, production uniquement ; ADR 0007).
 * - Identité (depuis /api/parametres) : favicons, nom du site, codes de vérification Google et Meta.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);
  private readonly site = inject(ParametresSiteService);

  private readonly page = signal<InformationsPage>({ titre: '' });

  /** URL publique du site, sans barre finale (configuration du serveur). */
  readonly urlSite = computed(() => this.site.parametres()?.site?.url?.replace(/\/+$/, '') ?? '');
  /** Faux tant que les paramètres ne sont pas arrivés : jamais d'indexation par défaut. */
  private readonly indexationAutorisee = computed(() => this.site.parametres()?.site?.indexationAutorisee === true);

  constructor() {
    // Le nom du site et l'URL publique arrivent avec les paramètres : tout est recalculé à ce moment-là
    effect(() => {
      const { titre, description, indexable = true, canonique, image, type = 'website', prix } = this.page();
      const nom = this.site.nomSite();
      this.title.setTitle(titre ? `${titre} | ${nom}` : nom);
      this.metaFacultative({ name: 'description' }, description);
      const indexer = indexable && this.indexationAutorisee();
      this.meta.updateTag({ name: 'robots', content: indexer ? 'index,follow' : 'noindex,follow' });

      const url = canonique ? this.urlAbsolue(canonique) : null;
      this.lienCanonique(url);
      this.metaFacultative({ property: 'og:url' }, url);
      this.meta.updateTag({ property: 'og:title', content: titre || nom });
      this.metaFacultative({ property: 'og:description' }, description);
      this.meta.updateTag({ property: 'og:type', content: type });
      this.metaFacultative(
        { property: 'og:image' },
        this.urlAbsolue(image || this.site.parametres()?.imagePartageUrl || ''),
      );
      this.meta.updateTag({ name: 'twitter:card', content: 'summary_large_image' });
      const prixProduit = type === 'product' && prix ? String(prix) : null;
      this.metaFacultative({ property: 'product:price:amount' }, prixProduit);
      this.metaFacultative({ property: 'product:price:currency' }, prixProduit ? 'XOF' : null);
    });
    effect(() => this.appliquerIdentite());
    effect(() => this.appliquerDonneesStructurees());
  }

  definirPage(informations: InformationsPage): void {
    this.page.set(informations);
  }

  /** « /demo/images/a.webp » → « https://boutique.sn/demo/images/a.webp » ; une URL absolue est gardée. */
  urlAbsolue(chemin: string): string {
    if (!chemin || /^https?:\/\//i.test(chemin)) return chemin;
    return `${this.urlSite()}${chemin.startsWith('/') ? '' : '/'}${chemin}`;
  }

  /** Un seul script JSON-LD par page (`@graph`), remplacé à chaque navigation. */
  private appliquerDonneesStructurees(): void {
    const parametres = this.site.parametres();
    const urlSite = this.urlSite();
    const { donneesStructurees } = this.page();
    let script = this.document.getElementById(ID_SCRIPT_LD);
    if (!parametres || !urlSite) {
      script?.remove();
      return;
    }
    const nom = this.site.nomSite();
    const blocs = [
      organisation(parametres, nom, urlSite),
      boutique(parametres, nom, urlSite),
      ...(donneesStructurees?.(urlSite) ?? []),
    ];
    if (!script) {
      script = this.document.createElement('script');
      script.setAttribute('type', 'application/ld+json');
      script.id = ID_SCRIPT_LD;
      this.document.head.appendChild(script);
    }
    script.textContent = serialiserLd(graphe(blocs));
  }

  private appliquerIdentite(): void {
    const parametres = this.site.parametres();
    this.meta.updateTag({ property: 'og:site_name', content: this.site.nomSite() });
    this.meta.updateTag({ property: 'og:locale', content: 'fr_FR' });
    if (!parametres) {
      return;
    }
    this.metaFacultative({ name: 'google-site-verification' }, parametres.verification?.google);
    this.metaFacultative({ name: 'facebook-domain-verification' }, parametres.verification?.meta);
    const favicons = parametres.favicons;
    if (favicons) {
      this.lien('icon', favicons.taille32, { sizes: '32x32', type: 'image/png' });
      this.lien('icon', favicons.manifeste192, { sizes: '192x192', type: 'image/png' });
      this.lien('apple-touch-icon', favicons.appleTouch180, { sizes: '180x180' });
    }
  }

  private metaFacultative(selecteur: { name?: string; property?: string }, valeur: string | null | undefined) {
    const attribut = selecteur.name ? `name="${selecteur.name}"` : `property="${selecteur.property}"`;
    if (valeur) {
      this.meta.updateTag({ ...selecteur, content: valeur } as { name: string; content: string });
    } else {
      this.meta.removeTag(attribut);
    }
  }

  private lienCanonique(url: string | null): void {
    const existant = this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!url) {
      existant?.remove();
      return;
    }
    this.lien('canonical', url, {});
  }

  /** Crée ou met à jour `<link rel="…" sizes="…">` dans le head (aucun doublon). */
  private lien(rel: string, href: string, attributs: Record<string, string>): void {
    const selecteur = `link[rel="${rel}"]` + (attributs['sizes'] ? `[sizes="${attributs['sizes']}"]` : '');
    let element = this.document.head.querySelector<HTMLLinkElement>(selecteur);
    if (!element) {
      element = this.document.createElement('link');
      element.setAttribute('rel', rel);
      this.document.head.appendChild(element);
    }
    for (const [nom, valeur] of Object.entries(attributs)) {
      element.setAttribute(nom, valeur);
    }
    element.setAttribute('href', href);
  }
}
