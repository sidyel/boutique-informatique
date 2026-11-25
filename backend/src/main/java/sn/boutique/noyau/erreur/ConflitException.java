package sn.boutique.noyau.erreur;

/** Action impossible dans l'état actuel de la ressource (retirer la dernière image d'un produit publié…). HTTP 409. */
public class ConflitException extends RuntimeException {

    private final String code;

    public ConflitException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
