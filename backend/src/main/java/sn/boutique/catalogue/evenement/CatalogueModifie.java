package sn.boutique.catalogue.evenement;

/**
 * Publié par toute écriture qui change ce que voient les visiteurs (produit, variante, prix, stock,
 * catégorie, marque, média, bannière, paramètres du site). Après la validation de la transaction, tous
 * les caches publics sont vidés (voir {@code InvalidationCaches}) : les écritures de l'équipe sont rares,
 * un vidage complet est plus sûr qu'une invalidation fine qui oublierait une liste ou une facette.
 *
 * @param origine ce qui a changé, pour les journaux (« variante 42 », « import démo »)
 */
public record CatalogueModifie(String origine) {
}
