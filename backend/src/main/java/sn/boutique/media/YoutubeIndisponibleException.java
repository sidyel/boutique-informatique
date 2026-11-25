package sn.boutique.media;

/** YouTube ne répond pas (délai dépassé, erreur serveur) : HTTP 502, le détail reste dans le journal. */
public class YoutubeIndisponibleException extends RuntimeException {

    public YoutubeIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
