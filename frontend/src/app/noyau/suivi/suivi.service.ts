import { Injectable } from '@angular/core';

import { ModeLivraison, ModePaiement, ProduitResume } from '../api/modeles';

/** Liste de produits vue : `item_list_id` GA4 (« accueil-nouveautes », « categorie-pc-portables »…). */
export interface ListeSuivie {
  id: string;
  nom: string;
}

/** Article d'un événement de panier ou de commande : `sku` = `item_id` GA4 = `content_ids` Meta = id des flux. */
export interface ArticleSuivi {
  sku: string;
  nom: string;
  marque: string;
  categorie: string;
  variante: string;
  prix: number;
  quantite: number;
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

  /**
   * Panier affiché (view_cart) : page panier ou ouverture du tiroir. Lignes commandables seulement, aux prix
   * recalculés par le serveur ; `valeur` = sous-total moins la remise (hors livraison).
   */
  voirPanier(_lignes: readonly ArticleSuivi[], _valeur: number): void {}

  /** Tunnel de commande ouvert (begin_checkout / InitiateCheckout). Une fois par visite du tunnel. */
  commencerCommande(_lignes: readonly ArticleSuivi[], _valeur: number): void {}

  /** Mode de livraison validé (add_shipping_info) ; `frais` en FCFA, 0 si offerts ou retrait. */
  choisirLivraison(_lignes: readonly ArticleSuivi[], _valeur: number, _mode: ModeLivraison, _frais: number): void {}

  /** Mode de paiement validé (add_payment_info / AddPaymentInfo). */
  choisirPaiement(_lignes: readonly ArticleSuivi[], _valeur: number, _mode: ModePaiement): void {}

  /**
   * Achat (purchase / Purchase) : appelé une seule fois par commande, quand le serveur a confirmé la vente
   * (commande payée ou confirmée par téléphone), jamais sur la seule foi du retour du navigateur.
   * `idEvenement` (« achat-CMD-… ») est partagé avec l'API Conversions pour le dédoublonnage.
   */
  achat(_numero: string, _lignes: readonly ArticleSuivi[], _valeur: number, _idEvenement: string): void {}

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
