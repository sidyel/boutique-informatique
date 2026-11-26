package sn.boutique.commande.paiement;

/**
 * Le prestataire de paiement est injoignable, refuse la demande, ou le paiement en ligne n'est pas configuré.
 * Le message est destiné au client ; le détail technique va dans les journaux, jamais dans la réponse. HTTP 503.
 */
public class PaiementIndisponibleException extends RuntimeException {

    public PaiementIndisponibleException(String message) {
        super(message);
    }

    public PaiementIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
