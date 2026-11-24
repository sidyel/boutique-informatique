import { Injectable, inject } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

export const NOM_SITE = 'Boutique informatique';

export interface InformationsPage {
  /** Titre sans le nom du site : « PC portables au meilleur prix au Sénégal ». */
  titre: string;
  description?: string;
  /** Pages privées, de test ou d'erreur : `noindex`. */
  indexable?: boolean;
}

/**
 * Service SEO central (version minimale de la phase 0).
 * La phase 3 ajoutera l'URL canonique, Open Graph, Twitter Card et le JSON-LD.
 * Fonctionne pendant le rendu serveur : les balises sont présentes dans le HTML envoyé.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);

  definirPage({ titre, description, indexable = true }: InformationsPage): void {
    this.title.setTitle(titre ? `${titre} | ${NOM_SITE}` : NOM_SITE);

    if (description) {
      this.meta.updateTag({ name: 'description', content: description });
    } else {
      this.meta.removeTag('name="description"');
    }

    this.meta.updateTag({
      name: 'robots',
      content: indexable ? 'index,follow' : 'noindex,follow',
    });
  }
}
