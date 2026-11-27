package sn.boutique.commande.facture;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compteur {@code produit.nombre_ventes} (unités vendues, tri « popularité » et section « Meilleures ventes »).
 * <p>
 * Une vente compte quand sa <strong>facture est émise</strong> (paiement confirmé, ou livraison encaissée), jamais à la
 * création de la commande : une commande abandonnée ou expirée ne fait pas monter un produit. Un avoir retire les
 * unités créditées. Les deux opérations sont déclenchées une seule fois par pièce (la facture est unique par commande,
 * l'avoir est émis une fois) et dans la transaction de la pièce : le compteur ne peut pas diverger d'elle.
 * <p>
 * L'écriture est un {@code UPDATE} relatif en SQL, jamais « lire puis écrire » : deux ventes simultanées s'additionnent.
 * Le compteur ne descend jamais sous zéro. Les caches publics expirent d'eux-mêmes (10 min) : une vente ne vide pas
 * tout le cache du catalogue.
 */
@Service
@RequiredArgsConstructor
public class VentesProduitService {

    private final JdbcClient jdbc;

    /** Ajoute les unités des lignes « article » de la facture. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void compterFacture(long factureId) {
        jdbc.sql("""
                        update produit p set nombre_ventes = p.nombre_ventes + s.unites
                          from (select v.produit_id, sum(lf.quantite) as unites
                                  from ligne_facture lf
                                  join ligne_commande lc on lc.id = lf.ligne_commande_id
                                  join variante_produit v on v.id = lc.variante_id
                                 where lf.facture_id = :facture and lf.type = 'ARTICLE'
                                 group by v.produit_id) s
                         where p.id = s.produit_id
                        """).param("facture", factureId).update();
    }

    /** Retire les unités créditées par l'avoir. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void retirerAvoir(long avoirId) {
        jdbc.sql("""
                        update produit p set nombre_ventes = greatest(0, p.nombre_ventes - s.unites)
                          from (select v.produit_id, sum(la.quantite) as unites
                                  from ligne_avoir la
                                  join ligne_facture lf on lf.id = la.ligne_facture_id
                                  join ligne_commande lc on lc.id = lf.ligne_commande_id
                                  join variante_produit v on v.id = lc.variante_id
                                 where la.avoir_id = :avoir and lf.type = 'ARTICLE'
                                 group by v.produit_id) s
                         where p.id = s.produit_id
                        """).param("avoir", avoirId).update();
    }
}
