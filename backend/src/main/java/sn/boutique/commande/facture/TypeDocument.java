package sn.boutique.commande.facture;

/** Types de pièces numérotées, chacun avec son compteur annuel sans trou. */
public enum TypeDocument {
    FACTURE("FAC"),
    AVOIR("AV");

    private final String prefixe;

    TypeDocument(String prefixe) {
        this.prefixe = prefixe;
    }

    public String prefixe() {
        return prefixe;
    }
}
