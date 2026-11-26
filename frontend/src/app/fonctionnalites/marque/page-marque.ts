import { NgOptimizedImage } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';

import { Filtres, Marque, Page, ProduitResume } from '../../noyau/api/modeles';
import { chargementMemorise } from '../../noyau/donnees/chargement-memorise';
import { ParametresSiteService } from '../../noyau/donnees/parametres-site.service';
import { referencementListe } from '../liste-produits/referencement-liste';
import { filAriane, listeProduits } from '../../noyau/seo/donnees-structurees';
import { SeoService } from '../../noyau/seo/seo.service';
import { FilAriane } from '../../partage/composants/fil-ariane/fil-ariane';
import { Squelette } from '../../partage/composants/squelette/squelette';
import { parametresFiltres } from '../categorie/page-categorie';
import { reponseDefinitive } from '../../noyau/seo/reponse-definitive';
import { PageIntrouvable } from '../erreurs/page-introuvable/page-introuvable';
import { Criteres, TRIS_LISTE, lireCriteres, pucesActives, titreListe, versParametres } from '../liste-produits/criteres';
import { ListeProduits } from '../liste-produits/liste-produits';
import { parametreChemin, parametresRoute } from '../liste-produits/parametres-route';
import { SourceListe, urlAvecParametres } from '../liste-produits/source-liste';

/**
 * Page marque `/marque/:slug` : logo, présentation, produits de la marque filtrables par catégorie, prix,
 * état et disponibilité. Marque inconnue : vraie 404.
 */
@Component({
  selector: 'bi-page-marque',
  imports: [FilAriane, ListeProduits, NgOptimizedImage, PageIntrouvable, Squelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (introuvable()) {
      <bi-page-introuvable />
    } @else {
      <div class="conteneur page">
        @if (courante(); as m) {
          <bi-fil-ariane [etapes]="[{ nom: m.nom }]" />
          <header class="entete entete--marque">
            @if (m.logoUrl) {
              <div class="logo">
                <img [ngSrc]="m.logoUrl" width="160" height="80" [alt]="'Logo ' + m.nom" priority />
              </div>
            }
            <div class="entete__textes">
              <h1 class="entete__titre">{{ m.nom }}</h1>
              @if (m.description) {
                <p class="entete__description">{{ m.description }}</p>
              }
            </div>
          </header>
        } @else {
          <div class="entete entete--squelette" aria-hidden="true">
            <bi-squelette largeur="8rem" hauteur="1rem" />
            <bi-squelette largeur="12rem" hauteur="2rem" />
          </div>
        }

        <bi-liste-produits
          [produits]="produits.valeur()"
          [filtres]="filtres.valeur()"
          [criteres]="criteres()"
          [chargement]="produits.chargement()"
          [invalide]="invalide()"
          [liste]="liste()"
          [source]="source"
        />
      </div>
    }
  `,
  styles: `
    @use 'page-liste';

    .entete--marque {
      grid-template-columns: auto minmax(0, 1fr);
      align-items: center;
      gap: var(--espace-4);
    }

    .entete__textes {
      display: grid;
      gap: var(--espace-2);
    }

    .logo {
      position: relative;
      display: grid;
      place-items: center;
      width: 5rem;
      height: 3.5rem;
      padding: var(--espace-2);
      border: var(--epaisseur-bordure) solid var(--couleur-bordure);
      border-radius: var(--rayon-2);
      background: var(--couleur-surface);

      img {
        width: 100%;
        height: 100%;
        object-fit: contain;
      }
    }
  `,
})
export class PageMarque {
  private readonly http = inject(HttpClient);
  private readonly site = inject(ParametresSiteService);

  protected readonly slug = parametreChemin('slug');
  private readonly parametres = parametresRoute();
  /** La marque vient du chemin : un paramètre `marque` dans l'URL est ignoré. */
  protected readonly criteres = computed<Criteres>(() => ({ ...lireCriteres(this.parametres()), marques: [] }));

  protected readonly marque = chargementMemorise<Marque>(() => `/api/marques/${this.slug()}`);
  protected readonly produits = chargementMemorise<Page<ProduitResume>>(() =>
    urlAvecParametres('/api/produits', { marque: this.slug(), ...versParametres(this.criteres()) }),
  );
  protected readonly filtres = chargementMemorise<Filtres>(() =>
    urlAvecParametres(`/api/marques/${this.slug()}/filtres`, parametresFiltres(this.criteres())),
  );

  /** API 404 : ancien slug (301) ou vraie 404. */
  private readonly definitive = reponseDefinitive(() =>
    !this.marque.chargement() && this.marque.statutErreur() === 404 ? `/marque/${this.slug()}` : null,
  );
  protected readonly introuvable = computed(
    () => this.definitive.introuvable() || (!this.marque.statutErreur() && this.filtres.statutErreur() === 404),
  );
  protected readonly invalide = computed(
    () => this.produits.statutErreur() === 400 || this.filtres.statutErreur() === 400,
  );

  protected readonly courante = computed(() => {
    const marque = this.marque.valeur();
    return marque?.slug === this.slug() ? marque : undefined;
  });

  protected readonly liste = computed(() => ({
    id: `marque-${this.slug()}`,
    nom: this.courante()?.nom ?? this.slug(),
  }));

  protected readonly source: SourceListe = {
    triParDefaut: 'recommande',
    tris: TRIS_LISTE,
    categoriesEnFiltre: true,
    filtresPour: (criteres) =>
      this.http.get<Filtres>(urlAvecParametres(`/api/marques/${this.slug()}/filtres`, parametresFiltres(criteres))),
    messageWhatsApp: (criteres) => {
      const nom = this.courante()?.nom ?? this.slug();
      const filtres = pucesActives(criteres, this.filtres.valeur()).map((p) => p.libelle);
      return (
        `Bonjour, je cherche un produit de la marque ${nom}` +
        (filtres.length ? ` (${filtres.join(', ')})` : '') +
        `. Pouvez-vous m'aider ?`
      );
    },
    origineWhatsApp: 'liste-vide',
  };

  constructor() {
    const seo = inject(SeoService);
    effect(() => {
      const marque = this.courante();
      if (!marque) return;
      seo.definirPage({
        ...referencementListe(`/marque/${marque.slug}`, this.criteres()),
        donneesStructurees: (url) => {
          const page = this.produits.valeur();
          return [
            filAriane([{ nom: marque.nom }], url),
            page?.elements.length ? listeProduits(page.elements, url, (page.page - 1) * page.taille) : null,
          ];
        },
        titre: titreListe(marque.nom, this.site.nomSite(), marque.metaTitre),
        description:
          marque.metaDescription?.trim() ||
          marque.description?.trim() ||
          `${marque.nom} au Sénégal : ${marque.nombreProduits} produits, prix en FCFA et disponibilité.`,
      });
    });
  }
}
