import { Injectable, inject, signal } from '@angular/core';

import { ProduitResume } from '../api/modeles';
import { PlateformeService } from '../plateforme.service';

const CLE = 'bi-recemment-consultes';
const MAXIMUM = 12;

/**
 * Produits récemment consultés, gardés dans le navigateur (localStorage) : rien ne part au serveur et
 * rien ne se passe pendant le rendu serveur. Un produit n'apparaît qu'une fois (sa dernière variante vue).
 */
@Injectable({ providedIn: 'root' })
export class RecemmentConsultesService {
  private readonly plateforme = inject(PlateformeService);

  /** Du plus récent au plus ancien. Vide côté serveur. */
  readonly produits = signal<ProduitResume[]>(this.lire());

  ajouter(produit: ProduitResume): void {
    if (!this.plateforme.estNavigateur) return;
    const liste = [produit, ...this.produits().filter((p) => p.id !== produit.id)].slice(0, MAXIMUM);
    this.produits.set(liste);
    try {
      localStorage.setItem(CLE, JSON.stringify(liste));
    } catch {
      // Navigation privée ou stockage plein : la liste reste en mémoire pour la visite
    }
  }

  private lire(): ProduitResume[] {
    if (!this.plateforme.estNavigateur) return [];
    try {
      const valeur: unknown = JSON.parse(localStorage.getItem(CLE) ?? '[]');
      return Array.isArray(valeur) ? valeur.filter(estResume).slice(0, MAXIMUM) : [];
    } catch {
      return [];
    }
  }
}

/** Garde contre un contenu modifié à la main ou d'une ancienne version du site. */
function estResume(valeur: unknown): valeur is ProduitResume {
  const p = valeur as Partial<ProduitResume> | null;
  return (
    !!p &&
    typeof p.id === 'number' &&
    typeof p.slug === 'string' &&
    typeof p.nom === 'string' &&
    typeof p.prix === 'number' &&
    typeof p.varianteId === 'number' &&
    !!p.marque &&
    typeof p.marque.nom === 'string'
  );
}
