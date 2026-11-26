import { ModeLivraison, ModePaiement, StatutCommande } from '../../../noyau/api/modeles';

export const LIBELLES_STATUT: Record<StatutCommande, string> = {
  EN_ATTENTE_PAIEMENT: 'En attente de paiement',
  EN_ATTENTE_CONFIRMATION: 'À confirmer par téléphone',
  CONFIRMEE: 'Commande confirmée',
  PAYEE: 'Paiement reçu',
  EN_PREPARATION: 'En préparation',
  EXPEDIEE: 'Expédiée',
  LIVREE: 'Livrée',
  ANNULEE: 'Annulée',
  REMBOURSEE: 'Remboursée',
};

export interface EtapeSuivi {
  statut: StatutCommande;
  libelle: string;
  etat: 'faite' | 'courante' | 'a-venir';
}

/**
 * Parcours d'une commande, avec l'étape atteinte. Le parcours dépend du paiement (en ligne : paiement reçu ;
 * à la livraison : confirmation par téléphone) et du mode de livraison (« prête à retirer » en boutique).
 * Une commande annulée ou remboursée n'a pas de parcours : elle est annoncée à part.
 */
export function parcours(
  statut: StatutCommande,
  modePaiement: ModePaiement,
  modeLivraison: ModeLivraison,
): EtapeSuivi[] {
  if (statut === 'ANNULEE' || statut === 'REMBOURSEE') return [];
  const debut: StatutCommande[] =
    modePaiement === 'A_LA_LIVRAISON'
      ? ['EN_ATTENTE_CONFIRMATION', 'CONFIRMEE']
      : ['EN_ATTENTE_PAIEMENT', 'PAYEE'];
  const ordre: StatutCommande[] = [...debut, 'EN_PREPARATION', 'EXPEDIEE', 'LIVREE'];
  const courant = ordre.indexOf(statut);
  return ordre.map((etape, i) => ({
    statut: etape,
    libelle: libelleEtape(etape, modeLivraison),
    etat: i < courant ? 'faite' : i === courant ? 'courante' : 'a-venir',
  }));
}

function libelleEtape(statut: StatutCommande, modeLivraison: ModeLivraison): string {
  if (statut === 'EXPEDIEE') return modeLivraison === 'RETRAIT_MAGASIN' ? 'Prête à retirer' : 'En cours de livraison';
  if (statut === 'LIVREE') return modeLivraison === 'RETRAIT_MAGASIN' ? 'Retirée' : 'Livrée';
  if (statut === 'EN_ATTENTE_CONFIRMATION') return 'Appel de confirmation';
  return LIBELLES_STATUT[statut];
}
