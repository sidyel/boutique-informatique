package sn.boutique.apresvente.dto;

import java.time.Instant;

/**
 * Avis publié sur la fiche produit. Aucune donnée permettant de reconnaître ou de contacter l'auteur :
 * ni e-mail, ni identifiant de compte, ni nom de famille complet.
 *
 * @param auteur       « Awa D. » ; après la suppression du compte, « Client vérifié » ou « Client » (ADR 0004 §3)
 * @param achatVerifie le client a reçu ce produit (commande livrée)
 * @param date         date de publication de l'avis
 */
public record AvisReponse(
        Long id,
        String auteur,
        boolean achatVerifie,
        int note,
        String titre,
        String commentaire,
        Instant date,
        String reponseBoutique,
        Instant dateReponse) {
}
