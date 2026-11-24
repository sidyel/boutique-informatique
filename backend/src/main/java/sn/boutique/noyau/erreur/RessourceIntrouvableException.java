package sn.boutique.noyau.erreur;

/**
 * Levée lorsqu'une ressource demandée n'existe pas (produit, catégorie, commande…).
 * Traduite en HTTP 404.
 */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }

    /** Exemple : {@code new RessourceIntrouvableException("Produit", "asus-tuf-a15")}. */
    public RessourceIntrouvableException(String typeRessource, Object identifiant) {
        super("%s introuvable : %s".formatted(typeRessource, identifiant));
    }
}
