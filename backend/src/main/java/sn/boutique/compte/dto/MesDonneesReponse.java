package sn.boutique.compte.dto;

import java.time.Instant;
import java.util.List;

/**
 * « Télécharger mes données » : tout ce que la boutique garde sur le client, dans un fichier lisible (droit d'accès).
 * Les montants sont en FCFA entiers, les dates en UTC (ISO 8601). Ne contient jamais le mot de passe, ni les jetons de
 * session, ni les coordonnées d'autres personnes.
 */
public record MesDonneesReponse(
        String note,
        Instant genereLe,
        Profil profil,
        List<Adresse> adresses,
        List<Commande> commandes,
        List<Consentement> consentements,
        List<ChangementEmail> changementsEmail,
        List<Avis> avis,
        List<String> commandesRattacheesDepuisInvite) {

    public record Profil(String email, String prenom, String nom, String telephone, boolean emailVerifie,
                         boolean consentementMarketing, Instant dateInscription) {
    }

    public record Adresse(String libelle, String nomComplet, String telephone, String ville, String quartier,
                          String adresse, String pointDeRepere, boolean parDefaut) {
    }

    public record Commande(String numero, Instant date, String statut, String modeLivraison, String modePaiement,
                           String livraisonNomComplet, String livraisonTelephone, String livraisonVille,
                           String livraisonQuartier, String livraisonAdresse, String livraisonPointDeRepere,
                           long sousTotal, long fraisLivraison, long remise, long total, String codePromo,
                           String numeroFacture, List<String> numerosAvoirs, List<Ligne> lignes,
                           List<Etape> historique, List<Paiement> paiements) {
    }

    public record Ligne(String produit, String variante, String sku, int quantite, long prixUnitaire, long total) {
    }

    public record Etape(String statut, Instant date) {
    }

    public record Paiement(String fournisseur, long montant, String statut, Instant date) {
    }

    public record Consentement(boolean accepte, String source, Instant date) {
    }

    public record ChangementEmail(String ancienEmail, String nouvelEmail, String statut, Instant date) {
    }

    public record Avis(String produit, int note, String titre, String commentaire, String statut,
                       boolean achatVerifie, Instant date) {
    }
}
