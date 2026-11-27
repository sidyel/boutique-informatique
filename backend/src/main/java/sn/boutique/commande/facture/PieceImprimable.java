package sn.boutique.commande.facture;

import java.time.Instant;
import java.util.List;

import sn.boutique.contenu.modele.RegimeTva;

/**
 * Tout ce qu'une facture ou un avoir imprime, déjà figé : le rendu PDF ne lit ni la base ni les paramètres du site.
 * Une même structure sert aux deux pièces ; pour un avoir, {@code factureOrigine} et {@code motif} sont renseignés.
 *
 * @param titre          « Facture » ou « Avoir »
 * @param numero         {@code FAC-2026-000001} ou {@code AV-2026-000001}
 * @param factureOrigine numéro de la facture d'origine (avoir seulement)
 * @param motif          motif de l'avoir (avoir seulement)
 * @param modePaiement   libellé lisible : « Wave, Orange Money… (en ligne) », « À la livraison »
 */
public record PieceImprimable(
        String titre,
        String numero,
        Instant dateEmission,
        String numeroCommande,
        String factureOrigine,
        String motif,
        Vendeur vendeur,
        Client client,
        String modePaiement,
        RegimeTva regimeTva,
        int tauxTvaBp,
        String mentionTva,
        List<Ligne> lignes,
        long totalTtc,
        long totalHt,
        long totalTva) {

    public record Vendeur(String raisonSociale, String ninea, String rccm, String adresse) {
    }

    public record Client(String nom, String telephone, String email, String adresse) {
    }

    /** Une ligne : un article, ou la livraison (référence vide). Montants en FCFA, TTC sauf mention contraire. */
    public record Ligne(String reference, String designation, int quantite, long prixUnitaireTtc, long remiseTtc,
                        long totalTtc, long totalHt, long montantTva) {
    }

    public boolean assujetti() {
        return regimeTva == RegimeTva.ASSUJETTI;
    }

    public boolean estAvoir() {
        return factureOrigine != null;
    }
}
