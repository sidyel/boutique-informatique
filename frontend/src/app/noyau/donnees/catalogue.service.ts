import { httpResource } from '@angular/common/http';
import { Injectable, computed } from '@angular/core';

import { CategorieNoeud, MarqueResume } from '../api/modeles';

/**
 * Arbre des catégories et marques : méga-menu, tiroir mobile, pied de page, tuiles de l'accueil.
 * Une seule lecture par rendu, partagée (réponses en cache côté API, transfer cache côté navigateur).
 */
@Injectable({ providedIn: 'root' })
export class CatalogueService {
  private readonly ressourceCategories = httpResource<CategorieNoeud[]>(() => '/api/categories');
  private readonly ressourceMarques = httpResource<MarqueResume[]>(() => '/api/marques');

  /** Catégories racines ayant au moins un produit. */
  readonly categories = computed(() =>
    (this.ressourceCategories.hasValue() ? this.ressourceCategories.value() : []).filter(
      (c) => c.nombreProduits > 0,
    ),
  );

  readonly categoriesChargees = computed(
    () => this.ressourceCategories.hasValue() || this.ressourceCategories.error() !== undefined,
  );

  /** Marques ayant des produits, les plus fournies d'abord. */
  readonly marques = computed(() =>
    (this.ressourceMarques.hasValue() ? this.ressourceMarques.value() : [])
      .filter((m) => m.nombreProduits > 0)
      .sort((a, b) => b.nombreProduits - a.nombreProduits || a.nom.localeCompare(b.nom, 'fr')),
  );
}
