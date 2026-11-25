package sn.boutique.contenu.modele;

/**
 * Élément du bandeau de confiance de l'accueil : « Livraison à Dakar en 24 h », « Garantie 12 mois »…
 * Saisi par l'équipe (aucune promesse écrite en dur dans le site). Un élément sans titre n'est pas affiché.
 *
 * @param icone pictogramme (liste fermée, dessinée par le frontend) ; {@code null} : sans pictogramme
 * @param titre texte principal, court : « Livraison 24 h à Dakar »
 * @param texte précision facultative : « Commande passée avant 16 h »
 */
public record ElementConfiance(IconeConfiance icone, String titre, String texte) {

    public enum IconeConfiance { LIVRAISON, GARANTIE, PAIEMENT, SAV, RETOUR, BOUTIQUE }

    /** Affichable : un titre non vide. */
    public boolean estAffichable() {
        return titre != null && !titre.isBlank();
    }
}
