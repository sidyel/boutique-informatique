package sn.boutique.stock.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.LigneCommande;
import sn.boutique.noyau.erreur.StockInsuffisantException;
import sn.boutique.stock.depot.MouvementStockDepot;
import sn.boutique.stock.modele.MouvementStock;
import sn.boutique.stock.modele.TypeMouvementStock;

/**
 * Réservation et libération du stock d'une commande (ADR 0008).
 * <p>
 * <strong>Anti-survente</strong> : chaque ligne est réservée par un seul {@code UPDATE ... WHERE stock >= q} :
 * atomique, le verrou de la ligne est pris par l'UPDATE lui-même. Si deux clients se disputent la dernière unité,
 * le second attend la fin de la transaction du premier, ne trouve plus de stock et reçoit une
 * {@link StockInsuffisantException} (409). Les variantes sont traitées par identifiant croissant : deux commandes
 * qui partagent plusieurs variantes les verrouillent dans le même ordre, sans interblocage.
 * <p>
 * Le compteur {@code version} est incrémenté à la main : le verrouillage optimiste de JPA (back-office) reste
 * cohérent. Après l'écriture SQL, variantes et produits sont relus ({@code refresh}) : {@code stockTotal} du
 * produit vient d'un trigger (ADR 0004 §2).
 * <p>
 * Doit s'exécuter dans la transaction de la commande : un échec sur la dernière ligne annule les précédentes.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private static final String AUTEUR_SYSTEME = "SYSTEME";

    private final JdbcClient jdbc;
    private final EntityManager entityManager;
    private final MouvementStockDepot mouvementDepot;
    private final Clock horloge;

    /**
     * Réserve le stock de toutes les lignes (mouvement SORTIE_VENTE).
     *
     * @throws StockInsuffisantException si une ligne n'est plus disponible dans la quantité demandée
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserver(Commande commande) {
        for (LigneCommande ligne : lignesParVariante(commande)) {
            VarianteProduit variante = ligne.getVariante();
            int stockApres = jdbc.sql("""
                            update variante_produit
                               set stock = stock - :quantite, version = version + 1, date_modification = :maintenant
                             where id = :id and actif and stock >= :quantite
                            returning stock
                            """)
                    .param("quantite", ligne.getQuantite())
                    .param("maintenant", maintenant())
                    .param("id", variante.getId())
                    .query(Integer.class)
                    .optional()
                    .orElseThrow(() -> new StockInsuffisantException(ligne.getSku(), ligne.getQuantite(),
                            stockActuel(variante.getId())));
            mouvementDepot.save(new MouvementStock(variante, -ligne.getQuantite(), stockApres,
                    TypeMouvementStock.SORTIE_VENTE, "Réservation de la commande " + commande.getNumero(), commande,
                    AUTEUR_SYSTEME));
        }
        relire(commande);
    }

    /**
     * Libère le stock réservé (mouvement ANNULATION). Une seule fois par commande : sans effet si le
     * stock a déjà été libéré.
     *
     * @return vrai si du stock a été remis en vente
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean liberer(Commande commande, String auteur, String motif) {
        if (mouvementDepot.existsByCommandeIdAndType(commande.getId(), TypeMouvementStock.ANNULATION)) {
            return false;
        }
        for (LigneCommande ligne : lignesParVariante(commande)) {
            VarianteProduit variante = ligne.getVariante();
            // Pas de condition « actif » : une variante masquée entre-temps récupère quand même son stock
            int stockApres = jdbc.sql("""
                            update variante_produit
                               set stock = stock + :quantite, version = version + 1, date_modification = :maintenant
                             where id = :id
                            returning stock
                            """)
                    .param("quantite", ligne.getQuantite())
                    .param("maintenant", maintenant())
                    .param("id", variante.getId())
                    .query(Integer.class)
                    .single();
            mouvementDepot.save(new MouvementStock(variante, ligne.getQuantite(), stockApres,
                    TypeMouvementStock.ANNULATION, motif, commande, auteur));
        }
        relire(commande);
        return true;
    }

    private static List<LigneCommande> lignesParVariante(Commande commande) {
        return commande.getLignes().stream()
                .sorted(Comparator.comparing(ligne -> ligne.getVariante().getId()))
                .toList();
    }

    private int stockActuel(long varianteId) {
        return jdbc.sql("select stock from variante_produit where id = :id").param("id", varianteId)
                .query(Integer.class).optional().orElse(0);
    }

    private OffsetDateTime maintenant() {
        return OffsetDateTime.ofInstant(horloge.instant(), ZoneOffset.UTC);
    }

    /** Variantes et produits relus : le stock a changé en SQL, {@code stockTotal} par un trigger. */
    private void relire(Commande commande) {
        entityManager.flush();
        Set<Produit> produits = new LinkedHashSet<>();
        for (LigneCommande ligne : commande.getLignes()) {
            VarianteProduit variante = ligne.getVariante();
            entityManager.refresh(variante);
            produits.add(variante.getProduit());
        }
        produits.forEach(entityManager::refresh);
    }
}
