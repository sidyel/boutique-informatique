package sn.boutique.media;

/**
 * Un média a été retiré en base : son fichier sera supprimé du stockage <strong>après</strong> la validation de
 * la transaction (si elle est annulée, le fichier doit rester).
 *
 * @param origine contexte lisible, enregistré si le fichier devient orphelin
 */
public record MediaRetire(String idPublic, StockageMedia.TypeRessource type, String origine) {
}
