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
  /** Bouton d'achat mis en avant sur la fiche produit ; absent (ancien cache) : PANIER. */
  boutonPrincipalFiche?: BoutonPrincipalFiche | null;
  /** « Livré à Dakar en 24 h, en régions en 2 à 4 jours » ; null : rien n'est affiché. */
  delaiLivraison?: string | null;
  /** Politique de retour (JSON-LD MerchantReturnPolicy). */
  politiqueRetour?: PolitiqueRetour | null;
  /** Adresse structurée ; null tant que rue, ville et région ne sont pas toutes saisies. */
  adresseStructuree?: AdresseStructuree | null;
  /** Boutique ouverte au public (JSON-LD Store) ; null : pas de boutique physique. */
  boutiquePhysique?: BoutiquePhysique | null;
  /** Adresse publique et autorisation d'indexer (configuration du serveur : URL_SITE, INDEXATION_AUTORISEE). */
  site?: { url: string; indexationAutorisee: boolean } | null;
}

export interface PolitiqueRetour {
  acceptes: boolean;
  /** Null si les retours sont refusés. */
  delaiJours: number | null;
  frais: 'GRATUIT' | 'A_LA_CHARGE_DU_CLIENT';
  enBoutique: boolean;
  parLivreur: boolean;
}

export interface AdresseStructuree {
  rue: string;
  ville: string;
  region: string;
  codePostal: string | null;
}

export type JourSemaine = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface BoutiquePhysique {
  latitude: number | null;
  longitude: number | null;
  horaires: { jours: JourSemaine[]; ouverture: string; fermeture: string }[];
}

export type BoutonPrincipalFiche = 'PANIER' | 'WHATSAPP';

export type TypeMedia = 'IMAGE' | 'VIDEO_FICHIER' | 'VIDEO_YOUTUBE';

/** Valeur propre à une variante (RAM, stockage…), qui remplace celle du tableau de caractéristiques. */
export interface ValeurVariante {
  cle: string;
  valeur: string;
  unite: string | null;
}

export interface VarianteFiche {
  id: number;
  /** = item_id des événements de suivi et id des flux produits. */
  sku: string;
  libelle: string;
  /** Axes de choix : `{ ram: '16 Go', stockage: '512 Go' }`. */
  attributs: Record<string, string>;
  prix: number;
  prixBarre: number | null;
  reduction: number | null;
  disponibilite: Disponibilite;
  poidsGrammes: number | null;
  caracteristiques: ValeurVariante[];
}

export interface MediaFiche {
  id: number;
  type: TypeMedia;
  url: string;
  urlMiniature: string | null;
  texteAlternatif: string | null;
  principal: boolean;
  /** Photo propre à une variante (une couleur) ; null : commune à toutes. */
  varianteId: number | null;
  idYoutube: string | null;
}

export interface CaracteristiqueFiche {
  cle: string;
  libelle: string;
  /** Mise en forme française (« 15,6 », « Oui ») ; l'unité est à part. */
  valeur: string;
  unite: string | null;
  /** Valeur propre aux variantes : remplacée par celle de la variante choisie. */
  parVariante: boolean;
}

export interface GroupeCaracteristiques {
  groupe: string;
  elements: CaracteristiqueFiche[];
}

/** Fiche produit complète (`/api/produits/{slug}`). */
export interface FicheProduit {
  id: number;
  slug: string;
  nom: string;
  descriptionCourte: string | null;
  /** HTML nettoyé par le backend (liste blanche), nettoyé à nouveau par Angular à l'affichage. */
  descriptionLongue: string | null;
  etat: EtatProduit;
  garantieMois: number;
  gtin: string | null;
  mpn: string | null;
  metaTitre: string | null;
  metaDescription: string | null;
  marque: { nom: string; slug: string; logoUrl: string | null };
  categorie: Lien;
  /** Catégories de la racine à celle du produit (incluse). */
  filAriane: Lien[];
  prixMinimum: number;
  disponibilite: Disponibilite;
  noteMoyenne: number | null;
  nombreAvis: number;
  /** Variantes actives, de la moins chère à la plus chère. */
  variantes: VarianteFiche[];
  medias: MediaFiche[];
  caracteristiques: GroupeCaracteristiques[];
}

/** Avis publié : aucune donnée personnelle (« Awa D. », « Client vérifié », « Client »). */
export interface Avis {
  id: number;
  auteur: string;
  achatVerifie: boolean;
  note: number;
  titre: string | null;
  commentaire: string | null;
  date: string;
  reponseBoutique: string | null;
  dateReponse: string | null;
}

export interface ZoneLivraison {
  nom: string;
  villesQuartiers: string[];
  /** Frais en FCFA. */
  tarif: number;
  delaiJours: number;
}

/** Page de résultats (`PageReponse` du backend) ; `page` commence à 1. */
export interface Page<T> {
  elements: T[];
  page: number;
  taille: number;
  totalElements: number;
  totalPages: number;
}

export interface OptionFiltre {
  valeur: string;
  libelle: string;
  nombre: number;
  selectionne: boolean;
}

export type TypeCaracteristique = 'NOMBRE' | 'TEXTE' | 'LISTE' | 'BOOLEEN';

export interface CaracteristiqueFiltre {
  cle: string;
  libelle: string;
  unite: string | null;
  type: TypeCaracteristique;
  groupe: string | null;
  valeurs: OptionFiltre[];
}

/** Facettes d'une catégorie ou d'une marque : chaque famille est comptée sans son propre filtre. */
export interface Filtres {
  totalProduits: number;
  /** Plage de prix des variantes qui conviennent, sans le filtre de prix. `null` si aucune. */
  prix: { min: number; max: number } | null;
  /** Catégorie : sous-catégories (liens). Marque : catégories proposées comme filtre. */
  sousCategories: OptionFiltre[];
  marques: OptionFiltre[];
  etats: OptionFiltre[];
  /** `null` : non compté (page de recherche), le filtre n'est pas proposé. */
  disponible: { enStock: number; selectionne: boolean } | null;
  caracteristiques: CaracteristiqueFiltre[];
}

export interface Categorie {
  nom: string;
  slug: string;
  description: string | null;
  imageUrl: string | null;
  banniereUrl: string | null;
  texteSeo: string | null;
  metaTitre: string | null;
  metaDescription: string | null;
  nombreProduits: number;
  /** Ancêtres, de la racine au parent (la catégorie elle-même n'y est pas). */
  filAriane: Lien[];
  enfants: CategorieNoeud[];
}

export interface Marque {
  nom: string;
  slug: string;
  logoUrl: string | null;
  description: string | null;
  metaTitre: string | null;
  metaDescription: string | null;
  nombreProduits: number;
}

export interface ResultatRecherche extends Page<ProduitResume> {
  requete: string;
  /** Aucun résultat exact : résultats par orthographe approchée. */
  approximatif: boolean;
  categories: OptionFiltre[];
  marques: OptionFiltre[];
  /** Rien trouvé : rayons et produits recommandés. */
  suggestions: { categories: Lien[]; produits: ProduitResume[] } | null;
}

export interface Suggestions {
  produits: ProduitResume[];
  categories: Lien[];
  marques: Lien[];
}
