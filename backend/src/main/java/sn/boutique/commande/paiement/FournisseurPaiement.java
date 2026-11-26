package sn.boutique.commande.paiement;

import java.util.List;

import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.commande.modele.TypeFournisseurPaiement;

/**
 * Prestataire de paiement en ligne (PayDunya en 4c). Le reste du code ne connaît que cette interface : les tests
 * utilisent un faux fournisseur et n'appellent jamais le vrai service.
 * <p>
 * Règle de confiance : le statut d'une commande ne passe à PAYEE que sur une réponse de {@link #verifier}
 * (appel serveur à serveur, avec nos clés), jamais sur ce que dit le navigateur ou le corps d'une notification.
 */
public interface FournisseurPaiement {

    TypeFournisseurPaiement type();

    /** Configuré (clés présentes) : sinon le paiement en ligne n'est pas proposé au client. */
    boolean disponible();

    /**
     * Crée la facture chez le prestataire.
     *
     * @throws PaiementIndisponibleException si le prestataire est injoignable ou refuse
     */
    SessionPaiement initier(DemandePaiement demande);

    /**
     * Interroge le prestataire sur une facture (serveur à serveur).
     *
     * @throws PaiementIndisponibleException si le prestataire est injoignable
     */
    ResultatVerification verifier(String referenceExterne);

    record Article(String nom, int quantite, long prixUnitaire) {
    }

    /**
     * @param montant total en FCFA (entier) ; devise XOF
     * @param email   peut être null
     */
    record DemandePaiement(String numeroCommande, long montant, ModePaiement mode, String nomClient,
                           String telephone, String email, List<Article> articles,
                           String urlRetour, String urlAnnulation, String urlNotification) {
    }

    /** @param urlPaiement page du prestataire vers laquelle rediriger le client */
    record SessionPaiement(String referenceExterne, String urlPaiement) {
    }

    enum Statut { EN_ATTENTE, REUSSI, ECHOUE, ANNULE }

    /**
     * @param montant        total en FCFA tel que connu du prestataire
     * @param devise         code ISO (XOF attendu)
     * @param numeroCommande numéro transmis à la création de la facture (contrôle de cohérence)
     */
    record ResultatVerification(Statut statut, long montant, String devise, String numeroCommande) {
    }
}
