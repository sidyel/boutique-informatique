package sn.boutique.media;

/** Le stockage des médias (Cloudinary) n'a pas pu traiter la demande : réseau, délai, configuration. */
public class StockageIndisponibleException extends RuntimeException {

    public StockageIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
