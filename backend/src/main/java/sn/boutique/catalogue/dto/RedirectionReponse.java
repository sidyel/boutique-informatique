package sn.boutique.catalogue.dto;

/**
 * Redirection à appliquer par le serveur SSR.
 *
 * @param destination chemin actuel (« /produit/nouveau-slug »)
 * @param statut      code HTTP de la redirection (301 : définitive)
 */
public record RedirectionReponse(String destination, int statut) {
}
