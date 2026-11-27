package sn.boutique.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import sn.boutique.commande.modele.TypeFournisseurPaiement;
import sn.boutique.commande.paiement.FournisseurPaiement;
import sn.boutique.commande.paiement.PaiementIndisponibleException;

/**
 * Prestataire de paiement factice : aucun test n'appelle PayDunya (ni aucun service réel). Chaque test remet le
 * faux à zéro avec {@link #reinitialiser()}.
 */
public class FauxFournisseurPaiement implements FournisseurPaiement {

    private final AtomicInteger compteur = new AtomicInteger();
    private final List<DemandePaiement> demandes = new CopyOnWriteArrayList<>();
    private volatile boolean disponible = true;
    private volatile boolean enPanne;
    private volatile long delaiMs;
    private volatile Runnable surAppel;
    private final AtomicInteger appelsEnTransaction = new AtomicInteger();

    public void reinitialiser() {
        demandes.clear();
        disponible = true;
        enPanne = false;
        delaiMs = 0;
        surAppel = null;
        appelsEnTransaction.set(0);
    }

    public void configurer(boolean disponible) {
        this.disponible = disponible;
    }

    /** Le prestataire ne répond plus : {@link #initier} lève {@link PaiementIndisponibleException}. */
    public void tomberEnPanne(boolean enPanne) {
        this.enPanne = enPanne;
    }

    /** Temps de réponse du prestataire : élargit la fenêtre de course des tests de concurrence. */
    public void repondreApres(long millisecondes) {
        this.delaiMs = millisecondes;
    }

    /** Action exécutée pendant l'appel au prestataire (par exemple vérifier qu'aucun verrou n'est tenu). */
    public void pendantLAppel(Runnable action) {
        this.surAppel = action;
    }

    /** Nombre d'appels faits alors qu'une transaction de base de données était ouverte : doit rester à zéro. */
    public int appelsEnTransaction() {
        return appelsEnTransaction.get();
    }

    public List<DemandePaiement> demandes() {
        return new ArrayList<>(demandes);
    }

    @Override
    public TypeFournisseurPaiement type() {
        return TypeFournisseurPaiement.PAYDUNYA;
    }

    @Override
    public boolean disponible() {
        return disponible;
    }

    @Override
    public SessionPaiement initier(DemandePaiement demande) {
        if (enPanne) {
            throw new PaiementIndisponibleException("Prestataire factice en panne");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            appelsEnTransaction.incrementAndGet();
        }
        Runnable action = surAppel;
        if (action != null) {
            action.run();
        }
        if (delaiMs > 0) {
            try {
                Thread.sleep(delaiMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        demandes.add(demande);
        String reference = "faux-" + compteur.incrementAndGet() + "-" + demande.numeroCommande();
        return new SessionPaiement(reference, "https://paiement.test/facture/" + reference);
    }

    @Override
    public ResultatVerification verifier(String referenceExterne) {
        throw new UnsupportedOperationException("Pas encore utilisé en 4b");
    }
}
