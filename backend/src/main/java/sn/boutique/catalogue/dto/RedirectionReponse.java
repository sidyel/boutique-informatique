package sn.boutique.catalogue.dto;

/**
 * Réponse définitive à appliquer par le serveur SSR pour une page qui n'existe plus à cette adresse
 * (ADR 0007).
 *
 * @param destination chemin actuel (« /produit/nouveau-slug ») pour une 301 ; null pour une 410
 * @param statut      301 (déplacée définitivement) ou 410 (retirée définitivement)
 * @param retrait     ce qui a été retiré, pour la page 410 ; null pour une 301
 */
public record RedirectionReponse(String destination, int statut, Retrait retrait) {

    public static final int DEFINITIVE = 301;
    public static final int DISPARUE = 410;

    /**
     * Page 410 : « Ce produit n'est plus vendu ».
     *
     * @param nom       nom du produit ou de la catégorie retiré
     * @param categorie rayon actif à proposer (produits similaires, lien) ; null s'il n'y en a plus
     */
    public record Retrait(String nom, LienReponse categorie) {
    }

    public static RedirectionReponse vers(String destination) {
        return new RedirectionReponse(destination, DEFINITIVE, null);
    }

    public static RedirectionReponse disparue(String nom, LienReponse categorie) {
        return new RedirectionReponse(null, DISPARUE, new Retrait(nom, categorie));
    }
}
