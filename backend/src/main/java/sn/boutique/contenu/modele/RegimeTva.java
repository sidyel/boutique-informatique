package sn.boutique.contenu.modele;

/** Régime de TVA du vendeur. Les prix du site sont toujours TTC. */
public enum RegimeTva {
    /** Assujetti : la TVA est extraite du prix TTC au taux réglé (18 % par défaut). */
    ASSUJETTI,
    /** TVA non applicable : aucune TVA, la mention réglée figure sur la facture. Régime par défaut. */
    NON_APPLICABLE
}
