package sn.boutique.commande.dto;

import java.time.Instant;

import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.commande.modele.StatutCommande;

/**
 * Réponse à la création d'une commande.
 *
 * @param jetonSuivi     lien de suivi sans compte : {@code /suivi/{jetonSuivi}}
 * @param urlPaiement    page du prestataire (paiement en ligne) ; null pour le paiement à la livraison, ou si le
 *                       prestataire n'a pas répondu (voir {@code paiementAReprendre})
 * @param paiementAReprendre le paiement en ligne n'a pas pu démarrer : la commande est créée, le stock réservé,
 *                       le client peut « Reprendre le paiement » depuis la page de suivi
 * @param expireLe       fin de la réservation tant que la commande attend un paiement ou un appel
 * @param rejouee        la même clé d'idempotence avait déjà créé cette commande : aucune nouvelle commande
 */
public record CommandeCreeeReponse(
        String numero,
        String jetonSuivi,
        StatutCommande statut,
        ModePaiement modePaiement,
        long total,
        Instant expireLe,
        String urlPaiement,
        boolean paiementAReprendre,
        boolean rejouee) {

    public CommandeCreeeReponse avecPaiement(String url, boolean aReprendre) {
        return new CommandeCreeeReponse(numero, jetonSuivi, statut, modePaiement, total, expireLe, url, aReprendre,
                rejouee);
    }
}
