import { Injectable } from '@angular/core';

import { ProduitResume } from '../api/modeles';

/** Liste de produits vue : `item_list_id` GA4 (« accueil-nouveautes », « categorie-pc-portables »…). */
export interface ListeSuivie {
  id: string;
  nom: string;
}

export type OrigineWhatsApp =
  | 'flottant'
  | 'entete'
  | 'pied-de-page'
  | 'fiche-produit'
  | 'liste-vide'
  | 'recherche-vide';

/**
 * Point d'entrée UNIQUE du suivi publicitaire (CLAUDE.md, « Règles de suivi publicitaire »).
 * Aucun composant n'appelle gtag ou fbq directement.
 *
 * Phase 3 : méthodes vides, appelées aux bons endroits. La phase 7 les remplira (dataLayer au format
 * e-commerce GA4, pixel Meta, event_id partagé avec l'API Conversions, consentement), sans toucher aux
 * composants. Les identifiants produit transmis sont toujours le SKU de la variante (= id des flux).
 */
@Injectable({ providedIn: 'root' })
export class SuiviService {
  /** Fiche produit affichée (view_item / ViewContent). */
  voirProduit(_produit: ProduitResume): void {}

  /** Liste de produits affichée (view_item_list). */
  voirListe(_liste: ListeSuivie, _produits: readonly ProduitResume[]): void {}

  /** Clic sur une carte produit dans une liste (select_item). */
  choisirProduit(_liste: ListeSuivie, _produit: ProduitResume, _position: number): void {}

  /** Recherche validée (search / Search). */
  rechercher(_terme: string): void {}

  /** Ajout au panier (add_to_cart / AddToCart). */
  ajouterAuPanier(_sku: string, _prix: number, _quantite: number): void {}

  /** Bannière promotionnelle vue ou cliquée (view_promotion / select_promotion). */
  voirPromotion(_idBanniere: number, _titre: string | null): void {}

  choisirPromotion(_idBanniere: number, _titre: string | null): void {}

  /**
   * Ouverture de WhatsApp (contact / Contact) : bouton flottant, en-tête, fiche produit, et demande
   * d'un produit introuvable (liste vide, recherche sans résultat).
   */
  contactWhatsApp(_origine: OrigineWhatsApp, _sku?: string): void {}

  /** Appel téléphonique depuis le site (contact). */
  contactTelephone(_origine: 'entete' | 'pied-de-page'): void {}
}
