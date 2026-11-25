/**
 * Types des réponses de l'API publique (miroir des DTO du backend).
 * Montants : entiers en FCFA.
 */

export interface Lien {
  nom: string;
  slug: string;
}

export interface Image {
  url: string;
  texteAlternatif: string | null;
}

export type EtatProduit = 'NEUF' | 'RECONDITIONNE' | 'OCCASION';
export type Disponibilite = 'EN_STOCK' | 'STOCK_FAIBLE' | 'RUPTURE';

export interface CaracteristiqueCarte {
  libelle: string;
  valeur: string;
}

/** Carte produit : prix, disponibilité et SKU de la variante retenue par l'API. */
export interface ProduitResume {
  id: number;
  slug: string;
  nom: string;
  marque: Lien;
  categorie: Lien;
  image: Image | null;
  varianteId: number;
  /** = item_id des événements de suivi et id des flux produits. */
  skuParDefaut: string;
  prix: number;
  prixBarre: number | null;
  reduction: number | null;
  etat: EtatProduit;
  disponibilite: Disponibilite;
  noteMoyenne: number | null;
  nombreAvis: number;
  nombreVariantes: number;
  caracteristiquesCles: CaracteristiqueCarte[] | null;
}

export type CodeSectionAccueil = 'nouveautes' | 'meilleures-ventes' | 'selection' | 'promotions';

export interface SectionAccueil {
  code: CodeSectionAccueil;
  /** Choisi par le serveur selon les données : ne jamais le remplacer côté client. */
  titre: string;
  produits: ProduitResume[];
}

export interface Accueil {
  sections: SectionAccueil[];
}

export interface CategorieNoeud {
  nom: string;
  slug: string;
  imageUrl: string | null;
  nombreProduits: number;
  enfants: CategorieNoeud[];
}

export interface MarqueResume {
  nom: string;
  slug: string;
  logoUrl: string | null;
  nombreProduits: number;
}

export type EmplacementBanniere =
  | 'ACCUEIL_CARROUSEL'
  | 'ACCUEIL_PROMO'
  | 'ENTETE_CATEGORIE'
  | 'TUNNEL_CONFIANCE'
  | 'PIED_DE_PAGE';

export interface Banniere {
  id: number;
  titre: string | null;
  sousTitre: string | null;
  texteBouton: string | null;
  lien: string | null;
  imageOrdinateurUrl: string;
  imageMobileUrl: string | null;
  texteAlternatif: string | null;
  /** Fin de l'opération (ISO 8601) : seule source d'un compte à rebours. */
  dateFin: string | null;
}

export type IconeConfiance = 'LIVRAISON' | 'GARANTIE' | 'PAIEMENT' | 'SAV' | 'RETOUR' | 'BOUTIQUE';

export interface ElementConfiance {
  icone: IconeConfiance | null;
  titre: string;
  texte: string | null;
}

export interface ParametresPublics {
  nomSite: string;
  logoUrl: string | null;
  logoSombreUrl: string | null;
  faviconUrl: string | null;
  favicons: {
    taille32: string;
    appleTouch180: string;
    manifeste192: string;
    manifeste512: string;
  } | null;
  imagePartageUrl: string | null;
  telephone: string | null;
  whatsapp: string | null;
  email: string | null;
  adresse: string | null;
  horaires: string | null;
  liensReseauxSociaux: Record<string, string>;
  messageBarreSuperieure: string | null;
  elementsConfiance: ElementConfiance[];
  verification: { google: string | null; meta: string | null };
}

export interface Suggestions {
  produits: ProduitResume[];
  categories: Lien[];
  marques: Lien[];
}
