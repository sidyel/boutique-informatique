package sn.boutique.commande.dto;

import java.time.Instant;

import sn.boutique.commande.modele.StatutCommande;

/**
 * Ligne de la liste « Mes commandes » : de quoi reconnaître la commande, sans le détail.
 *
 * @param premierArticle nom du premier article (« Asus TUF Gaming A15 »), pour reconnaître la commande d'un coup d'œil
 * @param imageUrl       image de ce premier article ; null s'il n'y en a pas
 */
public record CommandeResumeReponse(String numero, StatutCommande statut, Instant dateCreation, long total,
                                    int nombreArticles, String premierArticle, String imageUrl) {
}
