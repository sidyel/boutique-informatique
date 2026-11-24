package sn.boutique.commande.modele;

/**
 * Format du numéro lisible d'une commande : {@code CMD-2026-000123}.
 * Le compteur vient de la séquence PostgreSQL {@code sequence_numero_commande}
 * (unique et sans collision, même avec plusieurs instances du backend).
 */
public final class NumeroCommande {

    private NumeroCommande() {
    }

    public static String formater(int annee, long sequence) {
        if (sequence <= 0) {
            throw new IllegalArgumentException("La séquence doit être positive : " + sequence);
        }
        return "CMD-%d-%06d".formatted(annee, sequence);
    }

    /** {@code achat-CMD-2026-000123} : identifiant d'événement partagé par le pixel Meta et l'API Conversions. */
    public static String idEvenementAchat(String numero) {
        return "achat-" + numero;
    }
}
