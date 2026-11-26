import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Categorie, Filtres, Page, ProduitResume } from '../../noyau/api/modeles';
import { chargementMemorise } from '../../noyau/donnees/chargement-memorise';
import { ParametresSiteService } from '../../noyau/donnees/parametres-site.service';
import { referencementListe } from '../liste-produits/referencement-liste';
import { filAriane, listeProduits } from '../../noyau/seo/donnees-structurees';
import { SeoService } from '../../noyau/seo/seo.service';
import { EtapeAriane, FilAriane } from '../../partage/composants/fil-ariane/fil-ariane';
import { Squelette } from '../../partage/composants/squelette/squelette';
import { reponseDefinitive } from '../../noyau/seo/reponse-definitive';
import { PageRetiree } from '../erreurs/page-retiree/page-retiree';
import { PageIntrouvable } from '../erreurs/page-introuvable/page-introuvable';
import {
  Criteres,
  TRIS_LISTE,
  lireCriteres,
  pucesActives,
  titreListe,
  versParametres,
} from '../liste-produits/criteres';
import { ListeProduits } from '../liste-produits/liste-produits';
import { parametreChemin, parametresRoute } from '../liste-produits/parametres-route';
import { SourceListe, urlAvecParametres } from '../liste-produits/source-liste';

/** Paramètres des facettes : ni tri ni page (la même réponse sert à toutes les pages d'une liste). */
export function parametresFiltres(criteres: Criteres) {
  return versParametres({ ...criteres, tri: null, page: 1 });
}

/** Texte éditorial (texte brut, paragraphes séparés par une ligne vide). */
export function paragraphes(texte: string | null | undefined): string[] {
  return (texte ?? '')
    .split(/\n\s*\n/)
    .map((p) => p.trim())
    .filter(Boolean);
}

/**
 * Page catégorie `/categorie/:slug` : fil d'Ariane, titre, sous-rayons, liste filtrable, texte éditorial.
 * Rendue côté serveur avec ses produits ; catégorie inconnue : vraie 404.
 */
@Component({
  selector: 'bi-page-categorie',
  imports: [FilAriane, ListeProduits, PageIntrouvable, PageRetiree, RouterLink, Squelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './page-categorie.html',
  styleUrl: './page-categorie.scss',
})
export class PageCategorie {
  private readonly http = inject(HttpClient);
  private readonly site = inject(ParametresSiteService);

  protected readonly slug = parametreChemin('slug');

  private readonly parametres = parametresRoute();
  /** Le rayon vient du chemin : un paramètre `categorie` dans l'URL est ignoré. */
  protected readonly criteres = computed<Criteres>(() => ({ ...lireCriteres(this.parametres()), categorie: null }));

  protected readonly categorie = chargementMemorise<Categorie>(() => `/api/categories/${this.slug()}`);
  protected readonly produits = chargementMemorise<Page<ProduitResume>>(() =>
    urlAvecParametres('/api/produits', { categorie: this.slug(), ...versParametres(this.criteres()) }),
  );
  protected readonly filtres = chargementMemorise<Filtres>(() =>
    urlAvecParametres(`/api/categories/${this.slug()}/filtres`, parametresFiltres(this.criteres())),
  );

  /** API 404 : ancien slug ou catégorie désactivée (301 vers la parente, sinon 410), sinon vraie 404. */
  private readonly definitive = reponseDefinitive(() =>
    !this.categorie.chargement() && this.categorie.statutErreur() === 404 ? `/categorie/${this.slug()}` : null,
  );
  protected readonly introuvable = computed(
    () =>
      this.definitive.introuvable() ||
      // Seulement une fois la page lue sans erreur : sinon la 404 de cette liste, arrivée la première,
      // afficherait la page introuvable (statut 404) avant la réponse définitive (301, 410)
      (!this.categorie.chargement() && !this.categorie.statutErreur() && this.produits.statutErreur() === 404),
  );
  protected readonly retrait = this.definitive.retrait;
  protected readonly invalide = computed(
    () => this.produits.statutErreur() === 400 || this.filtres.statutErreur() === 400,
  );

  /** Catégorie affichée : celle du slug courant (pas la précédente pendant un changement de rayon). */
  protected readonly courante = computed(() => {
    const categorie = this.categorie.valeur();
    return categorie?.slug === this.slug() ? categorie : undefined;
  });

  protected readonly ariane = computed<EtapeAriane[]>(() => {
    const categorie = this.courante();
    if (!categorie) return [];
    return [
      ...categorie.filAriane.map((l) => ({ nom: l.nom, lien: `/categorie/${l.slug}` })),
      { nom: categorie.nom },
    ];
  });

  protected readonly sousRayons = computed(() =>
    (this.courante()?.enfants ?? []).filter((e) => e.nombreProduits > 0),
  );
  protected readonly texteSeo = computed(() => paragraphes(this.courante()?.texteSeo));

  protected readonly liste = computed(() => ({
    id: `categorie-${this.slug()}`,
    nom: this.courante()?.nom ?? this.slug(),
  }));

  protected readonly source: SourceListe = {
    triParDefaut: 'recommande',
    tris: TRIS_LISTE,
    categoriesEnFiltre: false,
    filtresPour: (criteres) =>
      this.http.get<Filtres>(urlAvecParametres(`/api/categories/${this.slug()}/filtres`, parametresFiltres(criteres))),
    messageWhatsApp: (criteres) => {
      const nom = this.courante()?.nom ?? this.slug();
      const filtres = pucesActives(criteres, this.filtres.valeur()).map((p) => p.libelle);
      return (
        `Bonjour, je cherche un produit dans la catégorie « ${nom} »` +
        (filtres.length ? ` (${filtres.join(', ')})` : '') +
        `. Pouvez-vous m'aider ?`
      );
    },
    origineWhatsApp: 'liste-vide',
  };

  constructor() {
    const seo = inject(SeoService);
    effect(() => {
      const categorie = this.courante();
      if (!categorie) return;
      seo.definirPage({
        ...referencementListe(`/categorie/${categorie.slug}`, this.criteres()),
        donneesStructurees: (url) => {
          const page = this.produits.valeur();
          return [
            filAriane(this.ariane(), url),
            page?.elements.length ? listeProduits(page.elements, url, (page.page - 1) * page.taille) : null,
          ];
        },
        titre: titreListe(categorie.nom, this.site.nomSite(), categorie.metaTitre),
        description:
          categorie.metaDescription?.trim() ||
          categorie.description?.trim() ||
          `${categorie.nom} au Sénégal : ${categorie.nombreProduits} produits, prix en FCFA et disponibilité.`,
      });
    });
  }
}
