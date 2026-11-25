import { Injectable, inject, signal } from '@angular/core';

import { SuiviService } from '../suivi/suivi.service';

/**
 * Panier : coquille de la phase 3 (compteur à 0, ajout sans effet). Le vrai panier (invité conservé,
 * contrôle du stock, synchronisation avec l'API) arrive en phase 4.
 */
@Injectable({ providedIn: 'root' })
export class PanierService {
  private readonly suivi = inject(SuiviService);

  /** Nombre d'articles affiché sur l'icône du panier. */
  readonly nombreArticles = signal(0);

  ajouter(sku: string, prix: number, quantite = 1): void {
    this.suivi.ajouterAuPanier(sku, prix, quantite);
  }
}
