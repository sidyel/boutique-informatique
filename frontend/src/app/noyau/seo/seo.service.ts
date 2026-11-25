import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

import { ParametresSiteService } from '../donnees/parametres-site.service';

export interface InformationsPage {
  /** Titre sans le nom du site : « PC portables au meilleur prix au Sénégal ». Vide : nom du site seul. */
  titre: string;
  description?: string;
  /** Pages privées, de test ou d'erreur : `noindex`. */
  indexable?: boolean;
}

/**
 * Service SEO central. Fonctionne pendant le rendu serveur : les balises sont dans le HTML envoyé.
 * - Page : titre « {titre} | {nom du site} », meta description, robots.
 * - Identité (depuis /api/parametres) : favicons, image Open Graph par défaut, nom du site, codes de
 *   vérification Google et Meta.
 * La phase 3d ajoutera la canonique, Open Graph par page, Twitter Card et le JSON-LD.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);
  private readonly site = inject(ParametresSiteService);

  private readonly page = signal<InformationsPage>({ titre: '' });

  constructor() {
    // Le nom du site arrive avec les paramètres : le titre est recalculé à ce moment-là
    effect(() => {
      const { titre, description, indexable = true } = this.page();
      const nom = this.site.nomSite();
      this.title.setTitle(titre ? `${titre} | ${nom}` : nom);
      if (description) {
        this.meta.updateTag({ name: 'description', content: description });
      } else {
        this.meta.removeTag('name="description"');
      }
      this.meta.updateTag({ name: 'robots', content: indexable ? 'index,follow' : 'noindex,follow' });
    });
    effect(() => this.appliquerIdentite());
  }

  definirPage(informations: InformationsPage): void {
    this.page.set(informations);
  }

  private appliquerIdentite(): void {
    const parametres = this.site.parametres();
    this.meta.updateTag({ property: 'og:site_name', content: this.site.nomSite() });
    this.meta.updateTag({ property: 'og:locale', content: 'fr_FR' });
    if (!parametres) {
      return;
    }
    this.metaFacultative({ property: 'og:image' }, parametres.imagePartageUrl);
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
