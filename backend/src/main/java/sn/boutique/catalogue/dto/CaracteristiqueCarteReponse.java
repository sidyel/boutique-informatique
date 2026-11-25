package sn.boutique.catalogue.dto;

/**
 * Caractéristique clé d'une carte produit : « RAM » → « 16 Go ». Valeur de la variante retenue si elle en
 * a une propre, sinon celle du produit.
 */
public record CaracteristiqueCarteReponse(String libelle, String valeur) {
}
