import { LignePanier, Panier } from '../api/modeles';

/** Données d'essai des tests du panier (lignes et paniers tels que les renvoie l'API). */
export function ligne(valeurs: Partial<LignePanier> = {}): LignePanier {
  return {
    id: 1,
    varianteId: 10,
    sku: 'ASUS-TUF-A15-16-512',
    nomProduit: 'TUF Gaming A15',
    slugProduit: 'asus-tuf-gaming-a15',
    marque: 'Asus',
    categorie: 'PC portables',
    libelleVariante: '16 Go / 512 Go',
    image: null,
    prixUnitaire: 450_000,
    prixPrecedent: null,
    quantite: 1,
    quantiteMax: 5,
    disponibilite: 'EN_STOCK',
    totalLigne: 450_000,
    probleme: null,
    ...valeurs,
  };
}

export function panier(lignes: LignePanier[], valeurs: Partial<Panier> = {}): Panier {
  const sousTotal = lignes.reduce((total, l) => total + l.totalLigne, 0);
  return {
    lignes,
    nombreArticles: lignes.reduce((n, l) => n + l.quantite, 0),
    sousTotal,
    codePromo: null,
    remise: 0,
    fraisLivraison: null,
    livraisonOfferte: false,
    total: sousTotal,
    prixModifies: false,
    commandable: lignes.length > 0,
    avertissements: [],
    ...valeurs,
  };
}
