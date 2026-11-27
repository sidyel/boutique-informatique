package sn.boutique.compte.evenement;

import java.util.List;

/**
 * Événements de l'espace client, publiés dans la transaction et traités après sa validation (e-mails en arrière-plan).
 * Les jetons des liens n'existent en clair que dans ces événements (jamais en base, jamais dans les journaux) :
 * {@code toString} les masque.
 */
public final class EvenementsCompte {

    private EvenementsCompte() {
    }

    /**
     * Demande de changement d'e-mail : lien de confirmation vers la nouvelle adresse, alerte avec lien « ce n'est pas
     * moi » vers l'ancienne.
     */
    public record ChangementEmailDemande(String prenom, String ancienEmail, String nouvelEmail,
                                         String jetonConfirmation, String jetonSignalement) {
        @Override
        public String toString() {
            return "ChangementEmailDemande[***]";
        }
    }

    /** Changement confirmé : l'ancienne adresse en est informée. */
    public record ChangementEmailConfirme(String prenom, String ancienEmail, String nouvelEmail) {
        @Override
        public String toString() {
            return "ChangementEmailConfirme[***]";
        }
    }

    /** Compte supprimé : un e-mail de confirmation part vers l'ancienne adresse (seul moment où elle est encore utilisée). */
    public record CompteSupprime(String prenom, String email) {
        @Override
        public String toString() {
            return "CompteSupprime[***]";
        }
    }

    /** Commandes passées en invité rattachées au compte : récapitulatif envoyé au titulaire. */
    public record CommandesRattachees(Long utilisateurId, String prenom, String email, List<String> numeros) {
        @Override
        public String toString() {
            return "CommandesRattachees[utilisateur=%d, %d commande(s)]".formatted(utilisateurId, numeros.size());
        }
    }
}
