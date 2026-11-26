package sn.boutique.commande.dto;

import java.time.Instant;
import java.util.List;

import sn.boutique.commande.modele.ModeLivraison;
import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.commande.modele.StatutCommande;

/**
 * Page de suivi d'une commande par son lien (sans compte). Rien d'inutile : pas d'e-mail ni d'adresse complète
 * (téléphone et adresse sont partiellement masqués), pas d'adresse IP, pas d'auteur des changements de statut,
 * pas d'attribution marketing.
 *
 * @param nomClient            prénom seulement, pour le « Merci Awa »
 * @param telephoneMasque      « +221 77 ••• •• 67 »
 * @param adresseMasquee       « Rue ••• , Médina, Dakar » ou le lieu de retrait ; null si rien à afficher
 * @param expireLe             fin de la réservation tant que la commande attend un paiement ou un appel
 * @param peutReprendrePaiement paiement en ligne non réglé, commande non expirée
 * @param achatASuivre         la commande est réglée ou confirmée et l'événement « achat » n'a pas encore été émis
 *                             pour les régies publicitaires
 */
public record SuiviCommandeReponse(
        String numero,
        StatutCommande statut,
        Instant dateCreation,
        Instant expireLe,
        String prenomClient,
        String telephoneMasque,
        ModeLivraison modeLivraison,
        String zoneLivraison,
        String adresseMasquee,
        ModePaiement modePaiement,
        List<Ligne> lignes,
        long sousTotal,
        long fraisLivraison,
        long remise,
        long total,
        String codePromo,
        List<Etape> etapes,
        boolean peutReprendrePaiement,
        boolean achatASuivre) {

    public record Ligne(String sku, String nomProduit, String libelleVariante, String imageUrl, long prixUnitaire,
                        int quantite, long totalLigne) {
    }

    /** Changement de statut tel que le client le voit (sans commentaire interne ni auteur). */
    public record Etape(StatutCommande statut, Instant date) {
    }
}
