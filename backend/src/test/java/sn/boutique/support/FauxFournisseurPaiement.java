package sn.boutique.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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

    public void reinitialiser() {
        demandes.clear();
        disponible = true;
        enPanne = false;
        delaiMs = 0;
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
